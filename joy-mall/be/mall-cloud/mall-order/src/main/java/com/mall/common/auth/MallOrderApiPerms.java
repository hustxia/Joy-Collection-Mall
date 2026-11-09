package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallOrder 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_order:*）。
 */
@Component
public class MallOrderApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_order/page", "mall_mall_order:list");
        m.put("GET /mall_order/tree", "mall_mall_order:list");
        m.put("GET /mall_order/options", "mall_mall_order:list");
        m.put("GET /mall_order/bind-values", "mall_mall_order:list");
        m.put("GET /mall_order/{id}", "mall_mall_order:list");
        m.put("GET /mall_order/{id}/with-children", "mall_mall_order:list");
        m.put("POST /mall_order/export", "mall_mall_order:list");
        m.put("GET /mall_order/import-template", "mall_mall_order:list");
        m.put("GET /mall_order/import/failed", "mall_mall_order:list");
        m.put("POST /mall_order", "mall_mall_order:add");
        m.put("PUT /mall_order", "mall_mall_order:edit");
        m.put("POST /mall_order/import", "mall_mall_order:add");
        m.put("POST /mall_order/save-with-children", "mall_mall_order:edit");
        m.put("DELETE /mall_order/{id}", "mall_mall_order:remove");
        // ===== 手写区：自定义端点权限映射（重新生成不会被覆盖） =====
        // V3 发货端点
        m.put("POST /mall_order/deliver", "mall_mall_order:edit");
        // V4 P2-1 关闭订单 / 管理员备注（写操作，漏注册即 403）
        m.put("POST /mall_order/close", "mall_mall_order:edit");
        m.put("POST /mall_order/remark", "mall_mall_order:edit");
        return m;
    }
}
