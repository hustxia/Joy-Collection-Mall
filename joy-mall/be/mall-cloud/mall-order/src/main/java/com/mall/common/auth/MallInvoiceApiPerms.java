package com.mall.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 发票端点-权限映射（V12）。
 */
@Component
public class MallInvoiceApiPerms implements ApiPermContributor {

    @Override
    public Map<String, String> endpointPerms() {
        Map<String, String> m = new HashMap<>();
        m.put("POST /mall_invoice/page", "mall_mall_invoice:list");
        m.put("POST /mall_invoice/issue", "mall_mall_invoice:issue");
        m.put("POST /mall_invoice/red-flush", "mall_mall_invoice:red");
        return m;
    }
}
