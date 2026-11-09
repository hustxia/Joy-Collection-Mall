package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MallSeckill 端点-权限映射（V8 手写，口径与代码生成器一致）。
 * perms 值与菜单注册/前端 v-hasPermi 同源（mall_mall_seckill:*）。
 */
@Component
public class MallSeckillApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_seckill/page", "mall_mall_seckill:list");
        m.put("GET /mall_seckill/{id}", "mall_mall_seckill:list");
        m.put("POST /mall_seckill", "mall_mall_seckill:add");
        m.put("PUT /mall_seckill", "mall_mall_seckill:edit");
        m.put("POST /mall_seckill/close/{id}", "mall_mall_seckill:edit");
        m.put("DELETE /mall_seckill/{id}", "mall_mall_seckill:remove");
        return m;
    }
}
