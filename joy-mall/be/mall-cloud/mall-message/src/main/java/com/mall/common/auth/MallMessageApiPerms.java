package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息台账端点-权限映射（V14）。
 *
 * <p>消息日志含会员手机号关联的接收人信息，列表也要权限点（不做 GET 缺省放行）。
 */
@Component
public class MallMessageApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("GET /mall_message_log/page", "mall_mall_message_log:list");
        m.put("GET /mall_message_log/template/page", "mall_mall_message_log:list");
        m.put("POST /mall_message_log/template", "mall_mall_message_log:edit");
        return m;
    }
}
