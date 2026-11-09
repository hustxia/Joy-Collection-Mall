package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 商家入驻端点-权限映射（perms 与 sys_menu 种子、前端 v-hasPermi 同源）。
 */
@Component
public class MallMerchantApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_merchant/page", "mall_mall_merchant:list");
        m.put("GET /mall_merchant/options", "mall_mall_merchant:list");
        m.put("GET /mall_merchant/{id}", "mall_mall_merchant:list");
        m.put("POST /mall_merchant/apply", "mall_mall_merchant:add");
        m.put("POST /mall_merchant/audit", "mall_mall_merchant:audit");
        m.put("POST /mall_merchant/status", "mall_mall_merchant:status");
        m.put("PUT /mall_merchant", "mall_mall_merchant:edit");
        return m;
    }
}
