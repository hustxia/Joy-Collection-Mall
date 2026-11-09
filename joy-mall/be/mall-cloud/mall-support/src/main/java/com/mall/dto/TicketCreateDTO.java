package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会员工单提交入参（V14）
 */
@Data
@Schema(description = "工单提交入参")
public class TicketCreateDTO {

    @Schema(description = "关联订单ID（可空）")
    private Long orderId;

    @Schema(description = "订单号（可空，便于客服检索）")
    private String orderNo;

    @Schema(description = "分类：1商品咨询2订单售后3物流4支付5其他")
    private Integer category;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "问题描述")
    private String content;

    @Schema(description = "优先级：1低2中3高")
    private Integer priority;
}
