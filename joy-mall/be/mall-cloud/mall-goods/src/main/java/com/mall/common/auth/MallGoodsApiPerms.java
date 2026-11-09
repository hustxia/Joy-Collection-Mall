package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallGoods 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_goods:*）。
 */
@Component
public class MallGoodsApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_goods/page", "mall_mall_goods:list");
        m.put("GET /mall_goods/tree", "mall_mall_goods:list");
        m.put("GET /mall_goods/options", "mall_mall_goods:list");
        m.put("GET /mall_goods/bind-values", "mall_mall_goods:list");
        m.put("GET /mall_goods/{id}", "mall_mall_goods:list");
        m.put("GET /mall_goods/{id}/with-children", "mall_mall_goods:list");
        m.put("POST /mall_goods/export", "mall_mall_goods:list");
        m.put("GET /mall_goods/import-template", "mall_mall_goods:list");
        m.put("GET /mall_goods/import/failed", "mall_mall_goods:list");
        m.put("POST /mall_goods", "mall_mall_goods:add");
        m.put("PUT /mall_goods", "mall_mall_goods:edit");
        m.put("POST /mall_goods/import", "mall_mall_goods:add");
        m.put("POST /mall_goods/save-with-children", "mall_mall_goods:edit");
        m.put("DELETE /mall_goods/{id}", "mall_mall_goods:remove");
        return m;
    }
}
