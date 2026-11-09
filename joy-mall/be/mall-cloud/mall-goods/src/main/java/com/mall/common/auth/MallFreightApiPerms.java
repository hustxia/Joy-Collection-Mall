package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 运费模板端点-权限映射（V12，perms 与 sys_menu 种子、前端 v-hasPermi 同源）。
 */
@Component
public class MallFreightApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_freight_template/page", "mall_mall_freight_template:list");
        m.put("GET /mall_freight_template/options", "mall_mall_freight_template:list");
        m.put("GET /mall_freight_template/{id}", "mall_mall_freight_template:list");
        m.put("POST /mall_freight_template", "mall_mall_freight_template:edit");
        m.put("PUT /mall_freight_template", "mall_mall_freight_template:edit");
        m.put("POST /mall_freight_template/default", "mall_mall_freight_template:edit");
        m.put("DELETE /mall_freight_template/{id}", "mall_mall_freight_template:edit");
        return m;
    }
}
