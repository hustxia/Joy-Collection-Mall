package com.mall.common.feign;

import com.mall.common.feign.dto.RefundAutoRequest;
import com.mall.common.feign.dto.RefundBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 售后域内部客户端：订单域列表/详情展示每个订单的最新售后单。
 * <p>静态地址：url 取 feign.clients.mall-refund，缺省 http://localhost:8206。
 * <p>唯一允许重试的客户端：本接口全部是 GET（幂等），故显式挂上
 * {@link RetryableReadFeignConfig}；其余客户端保持全局「不重试」。
 */
@FeignClient(name = "mall-refund", url = "${feign.clients.mall-refund:http://localhost:8206}",
        configuration = RetryableReadFeignConfig.class)
public interface RefundInnerClient {

    /** 批量取每个订单的最新一条售后单（一单多条历史只留最新）；无单的订单不在列表里 */
    @GetMapping("/inner/by-order-ids")
    List<RefundBrief> latestByOrderIds(@RequestParam("ids") List<Long> orderIds);

    /** 单订单最新售后单，无则 null */
    @GetMapping("/inner/latest-by-order/{orderId}")
    RefundBrief latestByOrderId(@PathVariable("orderId") Long orderId);

    /**
     * 自动退款（V12）：订单已关闭后收到支付成功回调时调用，落一条终态退款单。
     * <p>幂等：同订单已存在终态退款单时直接返回原单，不重复插入。
     */
    @PostMapping("/inner/auto-refund")
    RefundBrief autoRefund(@RequestBody RefundAutoRequest request);
}
