package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 商家提现端点-权限映射。
 */
@Component
public class MallMerchantWithdrawalApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_merchant_withdrawal/page", "mall_mall_merchant_withdrawal:list");
        m.put("POST /mall_merchant_withdrawal/apply", "mall_mall_merchant_withdrawal:add");
        m.put("POST /mall_merchant_withdrawal/audit", "mall_mall_merchant_withdrawal:audit");
        return m;
    }
}
