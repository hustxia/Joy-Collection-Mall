package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.dto.MallMerchantSettlementPageDTO;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantBillDetail;
import com.mall.entity.MallMerchantSettlement;
import com.mall.mapper.MallMerchantBillDetailMapper;
import com.mall.mapper.MallMerchantMapper;
import com.mall.mapper.MallMerchantSettlementMapper;
import com.mall.service.MallMerchantSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 结算单实现：把「待出账」（settlement_id 为空）的明细聚合成一张单据。
 *
 * <p>注意口径：商家余额在<b>订单完成</b>时已入账（见 MallMerchantBillDetailServiceImpl），
 * 结算单是账务凭证与对账依据，生成时不再二次加钱，只做归集与快照。
 */
@Slf4j
@Service
public class MallMerchantSettlementServiceImpl
        extends ServiceImpl<MallMerchantSettlementMapper, MallMerchantSettlement>
        implements MallMerchantSettlementService {

    private static final DateTimeFormatter NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MallMerchantMapper merchantMapper;
    private final MallMerchantBillDetailMapper detailMapper;

    public MallMerchantSettlementServiceImpl(MallMerchantMapper merchantMapper,
                                             MallMerchantBillDetailMapper detailMapper) {
        this.merchantMapper = merchantMapper;
        this.detailMapper = detailMapper;
    }

    @Override
    public Page<MallMerchantSettlement> pageQuery(MallMerchantSettlementPageDTO dto) {
        LambdaQueryWrapper<MallMerchantSettlement> wrapper = new LambdaQueryWrapper<>();
        if (dto.getMerchantId() != null) {
            wrapper.eq(MallMerchantSettlement::getMerchantId, dto.getMerchantId());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallMerchantSettlement::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallMerchantSettlement::getId);
        return page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallMerchantSettlement build(Long merchantId, LocalDateTime periodStart, LocalDateTime periodEnd,
                                        String operator) {
        if (merchantId == null) {
            throw new BusinessException(400, "参数错误：merchantId 必填");
        }
        MallMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(400, "商家不存在");
        }
        LambdaQueryWrapper<MallMerchantBillDetail> wrapper = new LambdaQueryWrapper<MallMerchantBillDetail>()
                .eq(MallMerchantBillDetail::getMerchantId, merchantId)
                .isNull(MallMerchantBillDetail::getSettlementId);
        if (periodStart != null) {
            wrapper.ge(MallMerchantBillDetail::getCreateTime, periodStart);
        }
        if (periodEnd != null) {
            wrapper.le(MallMerchantBillDetail::getCreateTime, periodEnd);
        }
        wrapper.orderByAsc(MallMerchantBillDetail::getId);
        List<MallMerchantBillDetail> pending = detailMapper.selectList(wrapper);
        if (pending.isEmpty()) {
            throw new BusinessException(400, "该商家没有待出账的结算明细");
        }

        long goodsAmount = pending.stream().mapToLong(d -> nz(d.getGoodsAmount())).sum();
        long platformFee = pending.stream().mapToLong(d -> nz(d.getPlatformFee())).sum();
        long refundAmount = pending.stream().mapToLong(d -> nz(d.getRefundAmount())).sum();
        LocalDateTime start = pending.stream().map(MallMerchantBillDetail::getCreateTime)
                .filter(java.util.Objects::nonNull).min(LocalDateTime::compareTo).orElse(periodStart);
        LocalDateTime end = pending.stream().map(MallMerchantBillDetail::getCreateTime)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(periodEnd);

        MallMerchantSettlement settlement = new MallMerchantSettlement();
        settlement.setSettlementNo(nextSettlementNo());
        settlement.setMerchantId(merchantId);
        settlement.setPeriodStart(start);
        settlement.setPeriodEnd(end);
        settlement.setOrderCount(pending.size());
        settlement.setGoodsAmount(goodsAmount);
        settlement.setPlatformFee(platformFee);
        settlement.setRefundAmount(refundAmount);
        settlement.setActualAmount(goodsAmount - refundAmount);
        settlement.setStatus(1);
        settlement.setSettleTime(LocalDateTime.now());
        save(settlement);

        // 只归集本次选中的明细（IN 精确匹配，避免把并发新到的明细误绑进本单）
        List<Long> ids = pending.stream().map(MallMerchantBillDetail::getId).toList();
        detailMapper.update(null, new LambdaUpdateWrapper<MallMerchantBillDetail>()
                .in(MallMerchantBillDetail::getId, ids)
                .isNull(MallMerchantBillDetail::getSettlementId)
                .set(MallMerchantBillDetail::getSettlementId, settlement.getId()));
        log.info("生成商家结算单：no={}, merchantId={}, orders={}, actual={}",
                settlement.getSettlementNo(), merchantId, pending.size(), settlement.getActualAmount());
        return settlement;
    }

    @Override
    public List<MallMerchantBillDetail> details(Long settlementId) {
        if (settlementId == null) {
            throw new BusinessException(400, "参数错误：settlementId 必填");
        }
        return detailMapper.selectList(new LambdaQueryWrapper<MallMerchantBillDetail>()
                .eq(MallMerchantBillDetail::getSettlementId, settlementId)
                .orderByAsc(MallMerchantBillDetail::getId));
    }

    private static String nextSettlementNo() {
        return "ST" + LocalDateTime.now().format(NO_TS)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
