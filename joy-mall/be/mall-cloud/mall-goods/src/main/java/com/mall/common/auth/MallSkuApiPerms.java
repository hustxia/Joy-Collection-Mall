package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallSku 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_sku:*）。
 */
@Component
public class MallSkuApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_sku/page", "mall_mall_sku:list");
        m.put("GET /mall_sku/tree", "mall_mall_sku:list");
        m.put("GET /mall_sku/options", "mall_mall_sku:list");
        m.put("GET /mall_sku/bind-values", "mall_mall_sku:list");
        m.put("GET /mall_sku/{id}", "mall_mall_sku:list");
        m.put("GET /mall_sku/{id}/with-children", "mall_mall_sku:list");
        m.put("POST /mall_sku/export", "mall_mall_sku:list");
        m.put("GET /mall_sku/import-template", "mall_mall_sku:list");
        m.put("GET /mall_sku/import/failed", "mall_mall_sku:list");
        m.put("POST /mall_sku", "mall_mall_sku:add");
        m.put("PUT /mall_sku", "mall_mall_sku:edit");
        m.put("POST /mall_sku/import", "mall_mall_sku:add");
        m.put("POST /mall_sku/save-with-children", "mall_mall_sku:edit");
        m.put("DELETE /mall_sku/{id}", "mall_mall_sku:remove");
        return m;
    }
}
