package com.mall.pay;

import org.springframework.stereotype.Component;

/**
 * 微信支付占位（V12）：接入时替换为真实实现（Native 下单、V3 回调验签、退款 API），
 * 并把 {@code mall.pay.wxpay.*} 配置补齐后改 {@code configured()} 返回 true。
 */
@Component
public class WxpayPayChannel extends UnconfiguredPayChannel {

    public static final String CODE = "wxpay";

    public WxpayPayChannel() {
        super(CODE, "微信支付未配置商户号与 API 证书，请使用沙箱渠道（sandbox）联调");
    }
}
