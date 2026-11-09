package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallCouponRecord 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_coupon_record:*）。
 */
@Component
public class MallCouponRecordApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_coupon_record/page", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/tree", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/options", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/bind-values", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/{id}", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/{id}/with-children", "mall_mall_coupon_record:list");
        m.put("POST /mall_coupon_record/export", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/import-template", "mall_mall_coupon_record:list");
        m.put("GET /mall_coupon_record/import/failed", "mall_mall_coupon_record:list");
        m.put("POST /mall_coupon_record", "mall_mall_coupon_record:add");
        m.put("PUT /mall_coupon_record", "mall_mall_coupon_record:edit");
        m.put("POST /mall_coupon_record/import", "mall_mall_coupon_record:add");
        m.put("POST /mall_coupon_record/save-with-children", "mall_mall_coupon_record:edit");
        m.put("DELETE /mall_coupon_record/{id}", "mall_mall_coupon_record:remove");
        return m;
    }
}
