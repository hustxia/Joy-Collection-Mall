package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.feign.MemberInnerClient;
import com.mall.entity.MallCommissionRecord;
import com.mall.entity.MallCommissionRule;
import com.mall.entity.MallDistributor;
import com.mall.mapper.MallCommissionRecordMapper;
import com.mall.service.MallCommissionRecordService;
import com.mall.service.MallCommissionRuleService;
import com.mall.service.MallDistributorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 佣金记录 Service 实现
 */
@Service
public class MallCommissionRecordServiceImpl
        extends ServiceImpl<MallCommissionRecordMapper, MallCommissionRecord>
        implements MallCommissionRecordService {

    private final MallDistributorService distributorService;
    private final MallCommissionRuleService ruleService;
    private final MemberInnerClient memberInnerClient;

    public MallCommissionRecordServiceImpl(MallDistributorService distributorService,
                                          MallCommissionRuleService ruleService,
                                          MemberInnerClient memberInnerClient) {
        this.distributorService = distributorService;
        this.ruleService = ruleService;
        this.memberInnerClient = memberInnerClient;
    }

    @Override
    @Transactional
    public void settle(Long distributorId, Long orderId, Long memberId, Long goodsId, Long payAmount) {
        if (distributorId == null || orderId == null) {
            return;
        }
        // 幂等：同一订单同一分销员只计一次
        MallCommissionRecord existed = getOne(new LambdaQueryWrapper<MallCommissionRecord>()
                .eq(MallCommissionRecord::getOrderId, orderId)
                .eq(MallCommissionRecord::getDistributorId, distributorId)
                .eq(MallCommissionRecord::getDeleted, 0));
        if (existed != null) {
            return;
        }

        long amount = calcAmount(goodsId, payAmount);
        if (amount <= 0) {
            return;
        }

        MallCommissionRecord rec = new MallCommissionRecord();
        rec.setDistributorId(distributorId);
        rec.setOrderId(orderId);
        rec.setMemberId(memberId);
        rec.setGoodsId(goodsId);
        rec.setAmount(amount);
        rec.setRate(rateOf(goodsId));
        rec.setStatus(1); // 订单完成即结算
        rec.setSettleTime(LocalDateTime.now());
        save(rec);

        MallDistributor d = distributorService.getById(distributorId);
        if (d != null) {
            d.setTotalCommission(add(d.getTotalCommission(), amount));
            d.setSettled(add(d.getSettled(), amount));
            d.setWithdrawable(add(d.getWithdrawable(), amount));
            distributorService.updateById(d);
        }
        try {
            memberInnerClient.commissionBalanceAdjust(memberId, amount);
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public void invalid(Long orderId, Long distributorId) {
        if (orderId == null) {
            return;
        }
        MallCommissionRecord rec = getOne(new LambdaQueryWrapper<MallCommissionRecord>()
                .eq(MallCommissionRecord::getOrderId, orderId)
                .eq(distributorId != null, MallCommissionRecord::getDistributorId, distributorId)
                .eq(MallCommissionRecord::getStatus, 1)
                .eq(MallCommissionRecord::getDeleted, 0));
        if (rec == null) {
            return;
        }
        rec.setStatus(2);
        updateById(rec);

        MallDistributor d = distributorService.getById(rec.getDistributorId());
        if (d != null) {
            d.setWithdrawable(sub(d.getWithdrawable(), rec.getAmount()));
            distributorService.updateById(d);
        }
        try {
            memberInnerClient.commissionBalanceAdjust(rec.getMemberId(), -rec.getAmount());
        } catch (Exception ignored) {
        }
    }

    private long calcAmount(Long goodsId, Long payAmount) {
        MallCommissionRule rule = ruleOf(goodsId);
        if (rule == null) {
            return 0;
        }
        if (rule.getFixed() != null && rule.getFixed() > 0) {
            return rule.getFixed();
        }
        if (rule.getRate() != null && rule.getRate() > 0 && payAmount != null) {
            return payAmount * rule.getRate() / 1000;
        }
        return 0;
    }

    private int rateOf(Long goodsId) {
        MallCommissionRule rule = ruleOf(goodsId);
        return rule == null || rule.getRate() == null ? 0 : rule.getRate();
    }

    private MallCommissionRule ruleOf(Long goodsId) {
        List<MallCommissionRule> goodsRules = ruleService.list(new LambdaQueryWrapper<MallCommissionRule>()
                .eq(MallCommissionRule::getScope, 1)
                .eq(MallCommissionRule::getGoodsId, goodsId)
                .eq(MallCommissionRule::getStatus, 1)
                .eq(MallCommissionRule::getDeleted, 0)
                .last("LIMIT 1"));
        if (goodsRules != null && !goodsRules.isEmpty()) {
            return goodsRules.get(0);
        }
        List<MallCommissionRule> globalRules = ruleService.list(new LambdaQueryWrapper<MallCommissionRule>()
                .eq(MallCommissionRule::getScope, 0)
                .eq(MallCommissionRule::getStatus, 1)
                .eq(MallCommissionRule::getDeleted, 0)
                .last("LIMIT 1"));
        if (globalRules != null && !globalRules.isEmpty()) {
            return globalRules.get(0);
        }
        return null;
    }

    private long add(Long a, long b) {
        return (a == null ? 0 : a) + b;
    }

    private long sub(Long a, long b) {
        long r = (a == null ? 0 : a) - b;
        return r < 0 ? 0 : r;
    }
}
