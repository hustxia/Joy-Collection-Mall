package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallAddress 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_address:*）。
 */
@Component
public class MallAddressApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_address/page", "mall_mall_address:list");
        m.put("GET /mall_address/tree", "mall_mall_address:list");
        m.put("GET /mall_address/options", "mall_mall_address:list");
        m.put("GET /mall_address/bind-values", "mall_mall_address:list");
        m.put("GET /mall_address/{id}", "mall_mall_address:list");
        m.put("GET /mall_address/{id}/with-children", "mall_mall_address:list");
        m.put("POST /mall_address/export", "mall_mall_address:list");
        m.put("GET /mall_address/import-template", "mall_mall_address:list");
        m.put("GET /mall_address/import/failed", "mall_mall_address:list");
        m.put("POST /mall_address", "mall_mall_address:add");
        m.put("PUT /mall_address", "mall_mall_address:edit");
        m.put("POST /mall_address/import", "mall_mall_address:add");
        m.put("POST /mall_address/save-with-children", "mall_mall_address:edit");
        m.put("DELETE /mall_address/{id}", "mall_mall_address:remove");
        return m;
    }
}
