package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallRefund 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_refund:*）。
 */
@Component
public class MallRefundApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_refund/page", "mall_mall_refund:list");
        m.put("GET /mall_refund/tree", "mall_mall_refund:list");
        m.put("GET /mall_refund/options", "mall_mall_refund:list");
        m.put("GET /mall_refund/bind-values", "mall_mall_refund:list");
        m.put("GET /mall_refund/{id}", "mall_mall_refund:list");
        m.put("GET /mall_refund/{id}/with-children", "mall_mall_refund:list");
        m.put("POST /mall_refund/export", "mall_mall_refund:list");
        m.put("GET /mall_refund/import-template", "mall_mall_refund:list");
        m.put("GET /mall_refund/import/failed", "mall_mall_refund:list");
        m.put("POST /mall_refund", "mall_mall_refund:add");
        m.put("PUT /mall_refund", "mall_mall_refund:edit");
        m.put("POST /mall_refund/import", "mall_mall_refund:add");
        m.put("POST /mall_refund/save-with-children", "mall_mall_refund:edit");
        m.put("DELETE /mall_refund/{id}", "mall_mall_refund:remove");
        // V4 售后闭环：审核与退款是写操作，必须注册权限点（fail-closed：漏注册 = 一律 403）
        m.put("POST /mall_refund/audit", "mall_mall_refund:edit");
        m.put("POST /mall_refund/refund", "mall_mall_refund:edit");
        // V15 售后扩展（REQ-P1-06）：换货节点与平台仲裁
        m.put("POST /mall_refund/confirm-receipt", "mall_mall_refund:edit");
        m.put("POST /mall_refund/send-exchange", "mall_mall_refund:edit");
        m.put("POST /mall_refund/arbitration/page", "mall_mall_refund:list");
        m.put("POST /mall_refund/arbitration/verdict", "mall_mall_refund:edit");
        return m;
    }
}
