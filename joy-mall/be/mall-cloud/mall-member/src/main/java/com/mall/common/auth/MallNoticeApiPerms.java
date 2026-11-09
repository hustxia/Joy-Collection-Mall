package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallNotice 端点-权限映射（V7 手写，照 MallAddressApiPerms 口径）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_notice:*）。
 */
@Component
public class MallNoticeApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_notice/page", "mall_mall_notice:list");
        return m;
    }
}
