package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 商家结算单端点-权限映射。
 */
@Component
public class MallMerchantSettlementApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_merchant_settlement/page", "mall_mall_merchant_settlement:list");
        m.put("GET /mall_merchant_settlement/{id}/details", "mall_mall_merchant_settlement:list");
        m.put("POST /mall_merchant_settlement/build", "mall_mall_merchant_settlement:settle");
        return m;
    }
}
