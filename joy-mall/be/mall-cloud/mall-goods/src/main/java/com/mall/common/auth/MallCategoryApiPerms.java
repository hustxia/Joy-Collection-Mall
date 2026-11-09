package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallCategory 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_category:*）。
 */
@Component
public class MallCategoryApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_category/page", "mall_mall_category:list");
        m.put("GET /mall_category/tree", "mall_mall_category:list");
        m.put("GET /mall_category/options", "mall_mall_category:list");
        m.put("GET /mall_category/bind-values", "mall_mall_category:list");
        m.put("GET /mall_category/{id}", "mall_mall_category:list");
        m.put("GET /mall_category/{id}/with-children", "mall_mall_category:list");
        m.put("POST /mall_category/export", "mall_mall_category:list");
        m.put("GET /mall_category/import-template", "mall_mall_category:list");
        m.put("GET /mall_category/import/failed", "mall_mall_category:list");
        m.put("POST /mall_category", "mall_mall_category:add");
        m.put("PUT /mall_category", "mall_mall_category:edit");
        m.put("POST /mall_category/import", "mall_mall_category:add");
        m.put("POST /mall_category/save-with-children", "mall_mall_category:edit");
        m.put("DELETE /mall_category/{id}", "mall_mall_category:remove");
        return m;
    }
}
