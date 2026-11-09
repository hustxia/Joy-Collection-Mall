package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MemberInnerClient;
import com.mall.entity.MallDistributor;
import com.mall.entity.MallWithdrawal;
import com.mall.mapper.MallWithdrawalMapper;
import com.mall.service.MallDistributorService;
import com.mall.service.MallWithdrawalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 分销员提现 Service 实现
 */
@Service
public class MallWithdrawalServiceImpl extends ServiceImpl<MallWithdrawalMapper, MallWithdrawal> implements MallWithdrawalService {

    private final MallDistributorService distributorService;
    private final MemberInnerClient memberInnerClient;

    public MallWithdrawalServiceImpl(MallDistributorService distributorService, MemberInnerClient memberInnerClient) {
        this.distributorService = distributorService;
        this.memberInnerClient = memberInnerClient;
    }

    @Override
    @Transactional
    public void apply(Long distributorId, Long amount, Integer method) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(400, "提现金额非法");
        }
        MallDistributor d = distributorService.getById(distributorId);
        if (d == null) {
            throw new BusinessException(404, "分销员不存在");
        }
        if (d.getStatus() != 1) {
            throw new BusinessException(400, "分销员未启用");
        }
        // 原子扣减会员佣金余额（余额不足 member 端返回 false）
        Boolean ok = memberInnerClient.commissionBalanceAdjust(d.getMemberId(), -amount);
        if (ok == null || !ok) {
            throw new BusinessException(400, "佣金余额不足");
        }
        MallWithdrawal w = new MallWithdrawal();
        w.setDistributorId(distributorId);
        w.setAmount(amount);
        w.setMethod(method == null ? 0 : method);
        w.setStatus(0);
        save(w);
    }

    @Override
    @Transactional
    public void audit(Long id, boolean pass, Long auditBy, String remark) {
        MallWithdrawal w = getById(id);
        if (w == null) {
            throw new BusinessException(404, "提现单不存在");
        }
        if (w.getStatus() != 0 && w.getStatus() != 1) {
            return;
        }
        MallDistributor d = distributorService.getById(w.getDistributorId());
        if (pass) {
            w.setStatus(2); // 已打款（余额已在申请时扣减）
        } else {
            w.setStatus(3); // 拒绝 → 退回会员余额
            if (d != null && d.getMemberId() != null) {
                try {
                    memberInnerClient.commissionBalanceAdjust(d.getMemberId(), w.getAmount());
                } catch (Exception ignored) {
                }
            }
        }
        w.setAuditBy(auditBy);
        w.setAuditRemark(remark);
        w.setAuditTime(LocalDateTime.now());
        updateById(w);
    }
}
