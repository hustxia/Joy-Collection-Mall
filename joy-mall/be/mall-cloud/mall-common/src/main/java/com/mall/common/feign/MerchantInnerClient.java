package com.mall.common.feign;

import com.mall.common.feign.dto.MerchantBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商家域内部客户端（V10）：订单完成结算、退款冲减、商品/订单展示店铺名。
 *
 * <p>静态地址：url 取 feign.clients.mall-merchant，缺省 http://localhost:8207。
 * <p>两个写端点在商家域内部按唯一键幂等，重复调用安全；调用方仍需按「失败只记日志、
 * 不影响主流程」的方式使用（订单完成 / 退款完成不能被下游拖挂）。
 */
@FeignClient(name = "mall-merchant", url = "${feign.clients.mall-merchant:http://localhost:8207}",
        fallback = com.mall.common.feign.fallback.MerchantInnerClientFallback.class)
public interface MerchantInnerClient {

    /** 店铺信息（展示用） */
    @GetMapping("/inner/merchant/info/{id}")
    MerchantBrief merchant(@PathVariable("id") Long id);

    /**
     * 订单完成结算入账（幂等）。
     *
     * @return true=本次入账；false=已结算过 / 商家未启用
     */
    @PostMapping("/inner/merchant/settle")
    Boolean settle(@RequestParam("orderId") Long orderId,
                   @RequestParam("merchantId") Long merchantId,
                   @RequestParam(value = "orderNo", required = false) String orderNo,
                   @RequestParam("payAmount") Long payAmount);

    /**
     * 退款冲减（幂等，按 refundId 去重）。
     *
     * @return true=本次冲减；false=已冲减过 / 无结算明细
     */
    @PostMapping("/inner/merchant/refund-adjust")
    Boolean refundAdjust(@RequestParam("refundId") Long refundId,
                         @RequestParam("orderId") Long orderId,
                         @RequestParam("merchantId") Long merchantId,
                         @RequestParam("refundAmount") Long refundAmount);
}
