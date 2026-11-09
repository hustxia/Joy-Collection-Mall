package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallMerchantAuditLog;
import com.mall.mapper.MallMerchantAuditLogMapper;
import com.mall.service.MallMerchantAuditLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商家审核流水实现
 */
@Service
public class MallMerchantAuditLogServiceImpl extends ServiceImpl<MallMerchantAuditLogMapper, MallMerchantAuditLog>
        implements MallMerchantAuditLogService {

    @Override
    public void log(Long merchantId, int action, String operator, String remark) {
        if (merchantId == null) {
            return;
        }
        MallMerchantAuditLog row = new MallMerchantAuditLog();
        row.setMerchantId(merchantId);
        row.setAction(action);
        row.setOperator(operator);
        row.setRemark(remark);
        save(row);
    }

    @Override
    public List<MallMerchantAuditLog> listByMerchant(Long merchantId) {
        return list(new LambdaQueryWrapper<MallMerchantAuditLog>()
                .eq(MallMerchantAuditLog::getMerchantId, merchantId)
                .orderByAsc(MallMerchantAuditLog::getId));
    }
}
