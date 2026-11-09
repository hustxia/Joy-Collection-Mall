package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 佣金台账端点-权限映射（V14 补齐）：查询挂分销台账，手动结算单独授权。
 */
@Component
public class MallCommissionApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_commission/page", "mall_mall_distributor:list");
        m.put("POST /mall_commission/settle", "mall_mall_distributor:audit");
        return m;
    }
}
