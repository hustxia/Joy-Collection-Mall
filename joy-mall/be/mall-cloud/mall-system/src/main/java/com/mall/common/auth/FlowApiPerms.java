package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程基座端点映射：审批流是平台内置能力，不接业务 perms，登录即可操作。
 * 值为空串 = 仅登录。若业务要求审批权限管控，可改为具体 perms 值。
 */
@Component
public class FlowApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /flow/submit", "");
        m.put("POST /flow/approve", "");
        m.put("POST /flow/reject", "");
        m.put("GET /flow/records", "");
        m.put("GET /flow/state", "");
        m.put("GET /flow/todo", "");
        m.put("GET /flow/done", "");
        m.put("GET /flow/task/{taskId}", "");
        return m;
    }
}
