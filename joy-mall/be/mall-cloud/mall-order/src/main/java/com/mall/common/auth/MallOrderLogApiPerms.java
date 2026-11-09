package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallOrderLog 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_order_log:*）。
 */
@Component
public class MallOrderLogApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_order_log/page", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/tree", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/options", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/bind-values", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/{id}", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/{id}/with-children", "mall_mall_order_log:list");
        m.put("POST /mall_order_log/export", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/import-template", "mall_mall_order_log:list");
        m.put("GET /mall_order_log/import/failed", "mall_mall_order_log:list");
        m.put("POST /mall_order_log", "mall_mall_order_log:add");
        m.put("PUT /mall_order_log", "mall_mall_order_log:edit");
        m.put("POST /mall_order_log/import", "mall_mall_order_log:add");
        m.put("POST /mall_order_log/save-with-children", "mall_mall_order_log:edit");
        m.put("DELETE /mall_order_log/{id}", "mall_mall_order_log:remove");
        return m;
    }
}
