package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.MallMerchantWithdrawalPageDTO;
import com.mall.entity.MallMerchantWithdrawal;

/**
 * 商家提现：申请即冻结余额，审核拒绝原路退回。
 */
public interface MallMerchantWithdrawalService extends IService<MallMerchantWithdrawal> {

    Page<MallMerchantWithdrawal> pageQuery(MallMerchantWithdrawalPageDTO dto);

    MallMerchantWithdrawal apply(Long merchantId, Long amount, Integer method);

    void audit(Long id, boolean pass, String rejectReason, String operator);
}
