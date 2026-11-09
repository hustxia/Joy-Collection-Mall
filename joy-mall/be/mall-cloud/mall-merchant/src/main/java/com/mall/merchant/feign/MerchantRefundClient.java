package com.mall.merchant.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 售后域商家内部端点（V11）：售后单分页与审核。
 *
 * <p>售后单本身没有 merchant_id，归属经订单判定；审核直接复用
 * {@code MallRefundService.audit(...)} 的状态机（1→2/3），商家域不重写。
 *
 * <p>静态地址：url 取 feign.clients.mall-refund，缺省 http://localhost:8206。
 */
@FeignClient(name = "mall-refund-merchant", url = "${feign.clients.mall-refund:http://localhost:8206}")
public interface MerchantRefundClient {

    @GetMapping("/inner/merchant/refund/page")
    Map<String, Object> page(@RequestParam("merchantId") Long merchantId,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "status", required = false) Integer status,
                             @RequestParam("pageNum") long pageNum,
                             @RequestParam("pageSize") long pageSize);

    @PostMapping("/inner/merchant/refund/audit")
    Map<String, Object> audit(@RequestParam("merchantId") Long merchantId,
                              @RequestParam("id") Long id,
                              @RequestParam("agree") boolean agree,
                              @RequestParam(value = "auditRemark", required = false) String auditRemark,
                              @RequestParam(value = "operator", required = false) String operator);
}
