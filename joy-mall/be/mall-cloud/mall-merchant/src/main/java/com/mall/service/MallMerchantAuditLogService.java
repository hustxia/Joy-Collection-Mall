package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallMerchantAuditLog;

import java.util.List;

/**
 * 商家审核流水
 */
public interface MallMerchantAuditLogService extends IService<MallMerchantAuditLog> {

    void log(Long merchantId, int action, String operator, String remark);

    /** 按商家查流水（时间线，正序） */
    List<MallMerchantAuditLog> listByMerchant(Long merchantId);
}
