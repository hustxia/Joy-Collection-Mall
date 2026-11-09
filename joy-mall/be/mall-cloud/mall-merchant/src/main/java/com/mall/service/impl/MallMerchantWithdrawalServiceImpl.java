package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.dto.MallMerchantWithdrawalPageDTO;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantWithdrawal;
import com.mall.mapper.MallMerchantMapper;
import com.mall.mapper.MallMerchantWithdrawalMapper;
import com.mall.service.MallMerchantWithdrawalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 商家提现实现。
 *
 * <p>申请：条件 UPDATE「余额足够才扣」，受影响 0 行即余额不足 —— 天然防并发超提；
 * 审核拒绝：把冻结金额原路退回（同样条件更新，避免重复退款）。
 */
@Slf4j
@Service
public class MallMerchantWithdrawalServiceImpl
        extends ServiceImpl<MallMerchantWithdrawalMapper, MallMerchantWithdrawal>
        implements MallMerchantWithdrawalService {

    public static final int ST_APPLY = 0;
    public static final int ST_AUDITING = 1;
    public static final int ST_PAID = 2;
    public static final int ST_REJECTED = 3;

    private final MallMerchantMapper merchantMapper;

    public MallMerchantWithdrawalServiceImpl(MallMerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    @Override
    public Page<MallMerchantWithdrawal> pageQuery(MallMerchantWithdrawalPageDTO dto) {
        LambdaQueryWrapper<MallMerchantWithdrawal> wrapper = new LambdaQueryWrapper<>();
        if (dto.getMerchantId() != null) {
            wrapper.eq(MallMerchantWithdrawal::getMerchantId, dto.getMerchantId());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallMerchantWithdrawal::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallMerchantWithdrawal::getId);
        return page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallMerchantWithdrawal apply(Long merchantId, Long amount, Integer method) {
        if (merchantId == null || amount == null || amount <= 0) {
            throw new BusinessException(400, "提现金额必须大于 0");
        }
        MallMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(400, "商家不存在");
        }
        if (!Integer.valueOf(1).equals(merchant.getStatus())) {
            throw new BusinessException(400, "店铺未启用，暂不可提现");
        }
        // 条件扣减：余额不足时 affected=0，不会扣成负数
        int affected = merchantMapper.update(null, new LambdaUpdateWrapper<MallMerchant>()
                .eq(MallMerchant::getId, merchantId)
                .apply("account_balance >= {0}", amount)
                .setSql("account_balance = account_balance - " + amount));
        if (affected == 0) {
            throw new BusinessException(400, "可提现余额不足");
        }
        MallMerchantWithdrawal withdrawal = new MallMerchantWithdrawal();
        withdrawal.setMerchantId(merchantId);
        withdrawal.setAmount(amount);
        withdrawal.setMethod(method == null ? 0 : method);
        withdrawal.setStatus(ST_APPLY);
        save(withdrawal);
        log.info("商家提现申请：merchantId={}, amount={}, withdrawalId={}", merchantId, amount, withdrawal.getId());
        return withdrawal;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String rejectReason, String operator) {
        if (id == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallMerchantWithdrawal withdrawal = getById(id);
        if (withdrawal == null) {
            throw new BusinessException(400, "提现单不存在");
        }
        if (!Integer.valueOf(ST_APPLY).equals(withdrawal.getStatus())
                && !Integer.valueOf(ST_AUDITING).equals(withdrawal.getStatus())) {
            throw new BusinessException(400, "该提现单已处理，请刷新后重试");
        }
        if (!pass && !StringUtils.hasText(rejectReason)) {
            throw new BusinessException(400, "拒绝必须填写原因");
        }
        String reason = pass ? null : rejectReason.trim();
        boolean updated = update(new LambdaUpdateWrapper<MallMerchantWithdrawal>()
                .eq(MallMerchantWithdrawal::getId, id)
                .in(MallMerchantWithdrawal::getStatus, ST_APPLY, ST_AUDITING)
                .set(MallMerchantWithdrawal::getStatus, pass ? ST_PAID : ST_REJECTED)
                .set(MallMerchantWithdrawal::getAuditBy, operator)
                .set(MallMerchantWithdrawal::getAuditTime, LocalDateTime.now())
                .set(MallMerchantWithdrawal::getRejectReason, reason));
        if (!updated) {
            throw new BusinessException(400, "提现单状态已变更，请刷新后重试");
        }
        if (!pass) {
            // 拒绝：冻结的金额原路退回
            merchantMapper.update(null, new LambdaUpdateWrapper<MallMerchant>()
                    .eq(MallMerchant::getId, withdrawal.getMerchantId())
                    .setSql("account_balance = account_balance + " + withdrawal.getAmount()));
        }
        log.info("商家提现审核：id={}, pass={}, operator={}", id, pass, operator);
    }
}
