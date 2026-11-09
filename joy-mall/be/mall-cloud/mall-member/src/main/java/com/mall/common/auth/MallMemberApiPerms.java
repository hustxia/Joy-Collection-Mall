package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallMember 端点-权限映射（代码生成器随 Controller 同源生成）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_member:*）。
 */
@Component
public class MallMemberApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_member/page", "mall_mall_member:list");
        m.put("GET /mall_member/tree", "mall_mall_member:list");
        m.put("GET /mall_member/options", "mall_mall_member:list");
        m.put("GET /mall_member/bind-values", "mall_mall_member:list");
        m.put("GET /mall_member/{id}", "mall_mall_member:list");
        m.put("GET /mall_member/{id}/with-children", "mall_mall_member:list");
        m.put("POST /mall_member/export", "mall_mall_member:list");
        m.put("GET /mall_member/import-template", "mall_mall_member:list");
        m.put("GET /mall_member/import/failed", "mall_mall_member:list");
        m.put("POST /mall_member", "mall_mall_member:add");
        m.put("PUT /mall_member", "mall_mall_member:edit");
        m.put("POST /mall_member/import", "mall_mall_member:add");
        m.put("POST /mall_member/save-with-children", "mall_mall_member:edit");
        m.put("DELETE /mall_member/{id}", "mall_mall_member:remove");
        return m;
    }
}
