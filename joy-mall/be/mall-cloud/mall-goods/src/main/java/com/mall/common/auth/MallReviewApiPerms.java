package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallReview 端点-权限映射（V7 商品评价后台）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_review:*）。
 */
@Component
public class MallReviewApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_review/page", "mall_mall_review:list");
        m.put("POST /mall_review/reply", "mall_mall_review:reply");
        m.put("POST /mall_review/hide", "mall_mall_review:hide");
        return m;
    }
}
