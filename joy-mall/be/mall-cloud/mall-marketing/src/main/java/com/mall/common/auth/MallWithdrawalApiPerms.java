package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 佣金提现端点-权限映射（V14 补齐）：审核动作对应 mall_mall_withdrawal:audit。
 */
@Component
public class MallWithdrawalApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_withdrawal/page", "mall_mall_distributor:list");
        m.put("POST /mall_withdrawal/audit", "mall_mall_withdrawal:audit");
        return m;
    }
}
