package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 自动退款请求（订单域 → 售后域，V12）。
 *
 * <p>触发场景固定一个：**订单已关闭（超时/取消）后又收到渠道支付成功回调**——
 * 钱已经收了但货发不出去，必须原路退回，否则就是对账里的长款。
 * 售后域据此落一条终态退款单，不回补库存（关单时已经回补过）。
 */
@Data
public class RefundAutoRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;

    private String orderNo;

    private Long memberId;

    /** 退款金额（分）：取支付单金额 */
    private Long amount;

    /** 退款渠道：sandbox / wxpay / alipay */
    private String channel;

    /** 原渠道交易号 */
    private String channelTradeNo;

    private String reason;
}
