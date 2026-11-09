package com.mall.pay;

import lombok.Data;

/**
 * 渠道回调解析结果（V12）：只携带「渠道说了什么」，是否推进订单由 PaymentService 决定。
 */
@Data
public class PayNotify {

    /** 本地支付单号 */
    private String payNo;

    /** 渠道交易号（微信 transaction_id / 支付宝 trade_no） */
    private String channelTradeNo;

    /** 渠道回传金额（分），必须与本地支付单一致 */
    private Long amount;

    /** 渠道状态：1 成功 2 失败 */
    private Integer status;

    /** 原始报文（留证） */
    private String raw;

    public boolean success() {
        return Integer.valueOf(1).equals(status);
    }
}
