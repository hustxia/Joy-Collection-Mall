package com.mall.merchant.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 订单域商家内部端点（V11）：列表/详情/发货。
 *
 * <p>发货不在商家域重写：请求原样转给订单域的 {@code DeliveryService.ship(...)}，
 * 部分发货、单号唯一、全发完转 30 这些规则只有一套实现。
 *
 * <p>静态地址：url 取 feign.clients.mall-order，缺省 http://localhost:8204。
 */
@FeignClient(name = "mall-order-merchant", url = "${feign.clients.mall-order:http://localhost:8204}")
public interface MerchantOrderClient {

    @GetMapping("/inner/merchant/order/page")
    Map<String, Object> page(@RequestParam("merchantId") Long merchantId,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "status", required = false) Integer status,
                             @RequestParam("pageNum") long pageNum,
                             @RequestParam("pageSize") long pageSize);

    @GetMapping("/inner/merchant/order/{id}")
    Map<String, Object> detail(@PathVariable("id") Long id, @RequestParam("merchantId") Long merchantId);

    /** body 与订单域 ShipDTO 同构：orderId / expressCompany / expressNo / remark / items[] */
    @PostMapping("/inner/merchant/order/{id}/deliver")
    Map<String, Object> deliver(@PathVariable("id") Long id,
                                @RequestParam("merchantId") Long merchantId,
                                @RequestParam(value = "operator", required = false) String operator,
                                @RequestBody Map<String, Object> body);
}
