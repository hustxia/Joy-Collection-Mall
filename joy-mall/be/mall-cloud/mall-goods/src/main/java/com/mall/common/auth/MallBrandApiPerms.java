package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallBrand 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_brand:*）。
 */
@Component
public class MallBrandApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_brand/page", "mall_mall_brand:list");
        m.put("GET /mall_brand/tree", "mall_mall_brand:list");
        m.put("GET /mall_brand/options", "mall_mall_brand:list");
        m.put("GET /mall_brand/bind-values", "mall_mall_brand:list");
        m.put("GET /mall_brand/{id}", "mall_mall_brand:list");
        m.put("GET /mall_brand/{id}/with-children", "mall_mall_brand:list");
        m.put("POST /mall_brand/export", "mall_mall_brand:list");
        m.put("GET /mall_brand/import-template", "mall_mall_brand:list");
        m.put("GET /mall_brand/import/failed", "mall_mall_brand:list");
        m.put("POST /mall_brand", "mall_mall_brand:add");
        m.put("PUT /mall_brand", "mall_mall_brand:edit");
        m.put("POST /mall_brand/import", "mall_mall_brand:add");
        m.put("POST /mall_brand/save-with-children", "mall_mall_brand:edit");
        m.put("DELETE /mall_brand/{id}", "mall_mall_brand:remove");
        return m;
    }
}
