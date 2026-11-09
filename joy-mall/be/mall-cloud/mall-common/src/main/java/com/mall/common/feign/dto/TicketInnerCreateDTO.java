package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 转人工建单入参（V16，ai 域 → 客服域 内部传输）。
 *
 * <p>{@code content} 由编排器生成：会话摘要 + 工具取证结果，人工接手不必再问一遍。
 */
@Data
public class TicketInnerCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long memberId;
    private Long orderId;
    private String orderNo;
    /** 1 商品咨询 2 订单售后 3 物流 4 支付 5 其他 */
    private Integer category;
    private String title;
    private String content;
    private Integer priority;
}
