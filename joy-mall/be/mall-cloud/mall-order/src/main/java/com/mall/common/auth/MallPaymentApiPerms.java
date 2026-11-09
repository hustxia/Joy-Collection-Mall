package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付流水与对账端点-权限映射（V12）。
 */
@Component
public class MallPaymentApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_payment/page", "mall_mall_payment:list");
        m.put("GET /mall_payment/channels", "mall_mall_payment:list");
        m.put("GET /mall_pay_reconcile/page", "mall_mall_pay_reconcile:list");
        m.put("POST /mall_pay_reconcile/run", "mall_mall_pay_reconcile:run");
        m.put("POST /mall_pay_reconcile/handle", "mall_mall_pay_reconcile:run");
        return m;
    }
}
