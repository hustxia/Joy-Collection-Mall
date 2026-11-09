package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallCoupon 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_coupon:*）。
 */
@Component
public class MallCouponApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_coupon/page", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/tree", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/options", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/bind-values", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/{id}", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/{id}/with-children", "mall_mall_coupon:list");
        m.put("POST /mall_coupon/export", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/import-template", "mall_mall_coupon:list");
        m.put("GET /mall_coupon/import/failed", "mall_mall_coupon:list");
        m.put("POST /mall_coupon", "mall_mall_coupon:add");
        m.put("PUT /mall_coupon", "mall_mall_coupon:edit");
        m.put("POST /mall_coupon/import", "mall_mall_coupon:add");
        m.put("POST /mall_coupon/save-with-children", "mall_mall_coupon:edit");
        m.put("DELETE /mall_coupon/{id}", "mall_mall_coupon:remove");
        return m;
    }
}
