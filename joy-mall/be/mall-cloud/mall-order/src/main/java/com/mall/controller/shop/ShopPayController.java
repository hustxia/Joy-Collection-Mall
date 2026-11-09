package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端支付（V3 mock → V12 渠道化）。
 *
 * <p>V12 起支付拆成三段，mock 只是其中一种渠道能力：
 * <ol>
 *   <li>{@code POST /shop/pay/prepay}：拿支付参数（二维码链接 / JSAPI 参数），落 {@code mall_payment}；</li>
 *   <li>{@code POST /shop/pay/notify/{channel}}：渠道回调（白名单 + 验签），幂等推进订单；</li>
 *   <li>{@code POST /shop/pay/mock}：沙箱直付，内部等价于「prepay + 自签名回调」，兼容旧前端与脚本。</li>
 * </ol>
 * 订单推进逻辑统一在 {@code OrderPaidService}，本类不再直接改订单表。
 */
@Tag(name = "商城前台-支付", description = "预支付 / 渠道回调 / 沙箱直付")
@RestController
@RequestMapping("/shop/pay")
public class ShopPayController {

    private final PaymentService paymentService;

    public ShopPayController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "预支付：返回渠道支付参数（沙箱/微信/支付宝）")
    @PostMapping("/prepay")
    public Result<Map<String, Object>> prepay(@RequestBody PrepayDTO dto) {
        Long memberId = UserContext.getUserId();
        return Result.ok(paymentService.prepay(memberId, dto.getOrderNo(), dto.getChannel(), dto.getTradeType()));
    }

    @Operation(summary = "渠道回调（免 token，验签；重复回调幂等）")
    @PostMapping("/notify/{channel}")
    public Result<Map<String, Object>> notify(@PathVariable("channel") String channel,
                                              @RequestBody(required = false) String rawBody) {
        return Result.ok(paymentService.notify(channel, rawBody));
    }

    @Operation(summary = "沙箱直付：等价 prepay + 回调，兼容旧前端；重复请求幂等")
    @PostMapping("/mock")
    public Result<Map<String, Object>> mock(@RequestBody MockPayDTO dto) {
        Long memberId = UserContext.getUserId();
        return Result.ok(paymentService.sandboxPayNow(memberId, dto.getOrderNo()));
    }

    @Operation(summary = "可用支付渠道（configured=false 的渠道前端不展示）")
    @GetMapping("/channels")
    public Result<Map<String, Boolean>> channels() {
        return Result.ok(paymentService.channelAvailability());
    }

    @Data
    public static class MockPayDTO {
        private String orderNo;
    }

    @Data
    public static class PrepayDTO {
        private String orderNo;
        /** 渠道：sandbox / wxpay / alipay，缺省 sandbox */
        private String channel;
        /** NATIVE / JSAPI / H5，缺省 NATIVE */
        private String tradeType;
    }
}
