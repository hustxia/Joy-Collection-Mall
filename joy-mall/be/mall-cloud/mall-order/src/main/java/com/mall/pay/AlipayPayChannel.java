package com.mall.pay;

import org.springframework.stereotype.Component;

/**
 * 支付宝占位（V12）：接入时替换为真实实现（电脑网站/手机网站下单、异步通知验签、退款）。
 */
@Component
public class AlipayPayChannel extends UnconfiguredPayChannel {

    public static final String CODE = "alipay";

    public AlipayPayChannel() {
        super(CODE, "支付宝未配置应用私钥与支付宝公钥，请使用沙箱渠道（sandbox）联调");
    }
}
