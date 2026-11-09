package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallWithdrawal;

/**
 * 分销员提现 Service
 */
public interface MallWithdrawalService extends IService<MallWithdrawal> {

    /** 申请提现：校验可提现余额，扣减会员佣金余额（冻结），落提现单 */
    void apply(Long distributorId, Long amount, Integer method);

    /** 审核：通过→已打款（余额已扣），驳回→退回会员余额 */
    void audit(Long id, boolean pass, Long auditBy, String remark);
}
