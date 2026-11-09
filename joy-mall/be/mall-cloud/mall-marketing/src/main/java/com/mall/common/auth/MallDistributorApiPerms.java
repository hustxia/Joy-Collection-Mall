package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 分销员端点-权限映射（V14 补齐）。
 *
 * <p>V9 上线时只注册了菜单，没有 ApiPermContributor，导致非 admin 角色
 * 「列表能进、审核被 fail-closed 拒绝」。这里按菜单 perms 补齐映射。
 */
@Component
public class MallDistributorApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_distributor/page", "mall_mall_distributor:list");
        m.put("GET /mall_distributor/{id}", "mall_mall_distributor:list");
        m.put("POST /mall_distributor/audit", "mall_mall_distributor:audit");
        m.put("POST /mall_distributor/enable/{id}", "mall_mall_distributor:audit");
        m.put("POST /mall_distributor/disable/{id}", "mall_mall_distributor:audit");
        return m;
    }
}
