package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 发货单端点-权限映射（V12）。
 */
@Component
public class MallDeliveryApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("GET /mall_delivery/page", "mall_mall_delivery:list");
        m.put("GET /mall_delivery/pending", "mall_mall_delivery:list");
        m.put("GET /mall_delivery/{id}", "mall_mall_delivery:list");
        m.put("POST /mall_delivery/ship", "mall_mall_delivery:ship");
        m.put("POST /mall_delivery/batch", "mall_mall_delivery:batch");
        return m;
    }
}
