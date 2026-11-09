package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallBanner 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_banner:*）。
 */
@Component
public class MallBannerApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_banner/page", "mall_mall_banner:list");
        m.put("GET /mall_banner/tree", "mall_mall_banner:list");
        m.put("GET /mall_banner/options", "mall_mall_banner:list");
        m.put("GET /mall_banner/bind-values", "mall_mall_banner:list");
        m.put("GET /mall_banner/{id}", "mall_mall_banner:list");
        m.put("GET /mall_banner/{id}/with-children", "mall_mall_banner:list");
        m.put("POST /mall_banner/export", "mall_mall_banner:list");
        m.put("GET /mall_banner/import-template", "mall_mall_banner:list");
        m.put("GET /mall_banner/import/failed", "mall_mall_banner:list");
        m.put("POST /mall_banner", "mall_mall_banner:add");
        m.put("PUT /mall_banner", "mall_mall_banner:edit");
        m.put("POST /mall_banner/import", "mall_mall_banner:add");
        m.put("POST /mall_banner/save-with-children", "mall_mall_banner:edit");
        m.put("DELETE /mall_banner/{id}", "mall_mall_banner:remove");
        return m;
    }
}
