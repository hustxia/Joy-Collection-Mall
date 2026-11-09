package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 客服工单端点-权限映射（V14）。
 *
 * <p>perms 与菜单种子（db/v14-experience.sql）同源：mall_mall_ticket:*。
 * 工单是售后敏感数据，列表与详情都要权限点，不做「GET 放行」的缺省。
 */
@Component
public class MallTicketApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_ticket/page", "mall_mall_ticket:list");
        m.put("GET /mall_ticket/{id}", "mall_mall_ticket:list");
        m.put("GET /mall_ticket/stat", "mall_mall_ticket:list");
        m.put("POST /mall_ticket/{id}/reply", "mall_mall_ticket:reply");
        m.put("PUT /mall_ticket/{id}/status", "mall_mall_ticket:edit");
        return m;
    }
}
