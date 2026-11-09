package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantBillDetail;
import com.mall.entity.MallMerchantRefundAdjust;
import com.mall.mapper.MallMerchantBillDetailMapper;
import com.mall.mapper.MallMerchantMapper;
import com.mall.mapper.MallMerchantRefundAdjustMapper;
import com.mall.service.MallMerchantBillDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 结算明细实现（V10 资金口径的唯一出口）。
 *
 * <p>抽成公式：platformFee = payAmount × settleRate ÷ 1000（四舍五入到分），
 * 商家应收 goodsAmount = payAmount − platformFee，两者相加恒等于实付。
 * <p>幂等：结算按 (order_id, merchant_id)，冲减按 refund_id；重复事件返回 false 不重复动账。
 */
@Slf4j
@Service
public class MallMerchantBillDetailServiceImpl
        extends ServiceImpl<MallMerchantBillDetailMapper, MallMerchantBillDetail>
        implements MallMerchantBillDetailService {

    private static final int MERCHANT_ENABLED = 1;

    private final MallMerchantMapper merchantMapper;
    private final MallMerchantRefundAdjustMapper refundAdjustMapper;

    public MallMerchantBillDetailServiceImpl(MallMerchantMapper merchantMapper,
                                             MallMerchantRefundAdjustMapper refundAdjustMapper) {
        this.merchantMapper = merchantMapper;
        this.refundAdjustMapper = refundAdjustMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleOrder(Long orderId, Long merchantId, String orderNo, Long payAmount) {
        if (orderId == null || merchantId == null || payAmount == null || payAmount <= 0) {
            log.warn("结算参数不完整，跳过：orderId={}, merchantId={}, payAmount={}", orderId, merchantId, payAmount);
            return false;
        }
        MallMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            log.warn("商家不存在，跳过结算：merchantId={}, orderId={}", merchantId, orderId);
            return false;
        }
        if (!Integer.valueOf(MERCHANT_ENABLED).equals(merchant.getStatus())) {
            // 停用/待审商家不入账：钱留在平台，待启用后由后台补结算
            log.warn("商家未启用，跳过结算：merchantId={}, status={}, orderId={}",
                    merchantId, merchant.getStatus(), orderId);
            return false;
        }
        // 幂等闸门 1：先查（唯一键兜底在 catch）
        long dup = count(new LambdaQueryWrapper<MallMerchantBillDetail>()
                .eq(MallMerchantBillDetail::getOrderId, orderId)
                .eq(MallMerchantBillDetail::getMerchantId, merchantId));
        if (dup > 0) {
            log.info("订单已结算过，幂等跳过：orderId={}, merchantId={}", orderId, merchantId);
            return false;
        }

        int rate = merchant.getSettleRate() == null ? 0 : merchant.getSettleRate();
        long platformFee = platformFeeOf(payAmount, rate);
        long goodsAmount = payAmount - platformFee;

        MallMerchantBillDetail detail = new MallMerchantBillDetail();
        detail.setOrderId(orderId);
        detail.setOrderNo(orderNo);
        detail.setMerchantId(merchantId);
        detail.setSettleRate(rate);
        detail.setPayAmount(payAmount);
        detail.setGoodsAmount(goodsAmount);
        detail.setPlatformFee(platformFee);
        detail.setRefundFlag(0);
        detail.setRefundAmount(0L);
        try {
            save(detail);
        } catch (DuplicateKeyException e) {
            // 幂等闸门 2：并发下另一请求已入账
            log.info("订单结算并发重复，幂等跳过：orderId={}, merchantId={}", orderId, merchantId);
            return false;
        }

        // 余额原子累加：用 SQL 自增，避免「读-改-写」丢更新
        merchantMapper.update(null, new LambdaUpdateWrapper<MallMerchant>()
                .eq(MallMerchant::getId, merchantId)
                .setSql("account_balance = account_balance + " + goodsAmount)
                .setSql("settled_total = settled_total + " + goodsAmount));
        log.info("订单结算入账：orderId={}, merchantId={}, pay={}, fee={}, merchant={}",
                orderId, merchantId, payAmount, platformFee, goodsAmount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundAdjust(Long refundId, Long orderId, Long merchantId, Long refundAmount) {
        if (refundId == null || orderId == null || merchantId == null
                || refundAmount == null || refundAmount <= 0) {
            log.warn("退款冲减参数不完整，跳过：refundId={}, orderId={}, merchantId={}, amount={}",
                    refundId, orderId, merchantId, refundAmount);
            return false;
        }
        // 幂等：同一售后单只冲减一次
        long dup = refundAdjustMapper.selectCount(new LambdaQueryWrapper<MallMerchantRefundAdjust>()
                .eq(MallMerchantRefundAdjust::getRefundId, refundId));
        if (dup > 0) {
            log.info("该售后单已冲减过，幂等跳过：refundId={}", refundId);
            return false;
        }
        MallMerchantBillDetail detail = getOne(new LambdaQueryWrapper<MallMerchantBillDetail>()
                .eq(MallMerchantBillDetail::getOrderId, orderId)
                .eq(MallMerchantBillDetail::getMerchantId, merchantId)
                .last("LIMIT 1"));
        if (detail == null) {
            // 订单还没完成结算（无明细），无需冲减
            log.info("订单无结算明细，跳过退款冲减：orderId={}, merchantId={}", orderId, merchantId);
            return false;
        }

        long pay = nz(detail.getPayAmount());
        long goods = nz(detail.getGoodsAmount());
        long already = nz(detail.getRefundAmount());
        long merchantShare = pay <= 0 ? 0L
                : BigDecimal.valueOf(refundAmount).multiply(BigDecimal.valueOf(goods))
                        .divide(BigDecimal.valueOf(pay), 0, RoundingMode.HALF_UP).longValue();
        // 冲减不超过商家应收，也不重复冲减
        merchantShare = Math.max(0L, Math.min(merchantShare, goods - already));
        long platformShare = Math.max(0L, refundAmount - merchantShare);

        MallMerchantRefundAdjust adjust = new MallMerchantRefundAdjust();
        adjust.setRefundId(refundId);
        adjust.setOrderId(orderId);
        adjust.setMerchantId(merchantId);
        adjust.setRefundAmount(refundAmount);
        adjust.setMerchantShare(merchantShare);
        adjust.setPlatformShare(platformShare);
        try {
            refundAdjustMapper.insert(adjust);
        } catch (DuplicateKeyException e) {
            log.info("退款冲减并发重复，幂等跳过：refundId={}", refundId);
            return false;
        }

        update(new LambdaUpdateWrapper<MallMerchantBillDetail>()
                .eq(MallMerchantBillDetail::getId, detail.getId())
                .set(MallMerchantBillDetail::getRefundFlag, 1)
                .set(MallMerchantBillDetail::getRefundAmount, already + merchantShare));

        if (merchantShare > 0) {
            // 余额与累计结算同步回退，且不允许负数（历史数据异常时不至于把商家扣成负余额）
            merchantMapper.update(null, new LambdaUpdateWrapper<MallMerchant>()
                    .eq(MallMerchant::getId, merchantId)
                    .setSql("account_balance = GREATEST(account_balance - " + merchantShare + ", 0)")
                    .setSql("settled_total = GREATEST(settled_total - " + merchantShare + ", 0)"));
        }
        log.info("退款冲减完成：refundId={}, orderId={}, merchantId={}, refund={}, merchantShare={}, platformShare={}",
                refundId, orderId, merchantId, refundAmount, merchantShare, platformShare);
        return true;
    }

    /** 平台抽成：实付 × 费率(‰) ÷ 1000，四舍五入到分 */
    public static long platformFeeOf(long payAmount, int rate) {
        if (payAmount <= 0 || rate <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(payAmount).multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValue();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
