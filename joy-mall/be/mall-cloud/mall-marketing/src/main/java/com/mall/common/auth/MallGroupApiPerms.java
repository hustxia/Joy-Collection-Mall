package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallGroup 端点-权限映射（V8 手写，口径与代码生成器一致）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_group:*）。
 */
@Component
public class MallGroupApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_group/page", "mall_mall_group:list");
        m.put("GET /mall_group/{id}", "mall_mall_group:list");
        m.put("POST /mall_group", "mall_mall_group:add");
        m.put("PUT /mall_group", "mall_mall_group:edit");
        m.put("POST /mall_group/team-page", "mall_mall_group:list");
        m.put("DELETE /mall_group/{id}", "mall_mall_group:remove");
        return m;
    }
}
