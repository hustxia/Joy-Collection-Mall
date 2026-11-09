package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallLottery 端点-权限映射（V8 手写，口径与代码生成器一致）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_lottery:*）。
 */
@Component
public class MallLotteryApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_lottery/page", "mall_mall_lottery:list");
        m.put("GET /mall_lottery/{id}", "mall_mall_lottery:list");
        m.put("POST /mall_lottery", "mall_mall_lottery:add");
        m.put("PUT /mall_lottery", "mall_mall_lottery:edit");
        m.put("POST /mall_lottery/prize-save", "mall_mall_lottery:edit");
        m.put("DELETE /mall_lottery/{id}", "mall_mall_lottery:remove");
        return m;
    }
}
