package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 促销活动端点-权限点映射（V13，与 db/v13-marketing.sql 的 sys_menu 种子、前端 v-hasPermi 同源）。
 */
@Component
public class MallPromotionApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_promotion/page", "mall_mall_promotion:list");
        m.put("GET /mall_promotion/dict", "mall_mall_promotion:list");
        m.put("GET /mall_promotion/{id}", "mall_mall_promotion:list");
        m.put("POST /mall_promotion", "mall_mall_promotion:edit");
        m.put("PUT /mall_promotion", "mall_mall_promotion:edit");
        m.put("POST /mall_promotion/status", "mall_mall_promotion:edit");
        m.put("DELETE /mall_promotion/{id}", "mall_mall_promotion:edit");
        return m;
    }
}
