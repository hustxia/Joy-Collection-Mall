package com.mall.pay;

import com.mall.entity.MallPayment;

import java.util.Map;

/**
 * 支付渠道 SPI（V12）。
 *
 * <p>接入新渠道只需实现本接口并注册为 Spring bean，订单与回调流程完全不变：
 * <ol>
 *   <li>{@link #prepay} 返回前端唤起支付所需的参数（二维码链接 / JSAPI 参数）；</li>
 *   <li>{@link #verifyNotify} 验签并解析回调，验签失败必须抛异常，绝不返回成功；</li>
 *   <li>订单推进统一由 {@code OrderPaidService} 负责，渠道实现不碰订单表。</li>
 * </ol>
 *
 * <p>当前实现：{@code sandbox}（本地/测试可用，HMAC-MD5 验签）与
 * {@code wxpay}/{@code alipay}（占位，未配置商户资质时 {@link #configured()} 为 false，
 * 调用直接 503，避免「看起来能付其实付不了」）。
 */
public interface PayChannel {

    /** 渠道编码：sandbox / wxpay / alipay */
    String code();

    /** 是否已配置可用（真实渠道需要商户号、证书、密钥） */
    boolean configured();

    /** 渠道下单：返回给前端的支付参数 */
    Map<String, Object> prepay(MallPayment payment);

    /** 回调验签与解析；验签失败抛 400 */
    PayNotify verifyNotify(String rawBody);
}
