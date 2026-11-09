package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 服务间消息发送入参（V14，{@code POST /inner/message/send}）。
 *
 * <p>调用方（order / refund）只需给场景码 + 接收人 + 变量，模板与通道由消息域决定；
 * 这样改文案不用改调用方代码。
 */
@Data
@Schema(description = "内部消息发送入参")
public class MessageSendDTO {

    @Schema(description = "场景码：PAY_SUCCESS/ORDER_SHIPPED/REFUND_ARRIVED")
    private String sceneCode;

    @Schema(description = "接收会员ID")
    private Long memberId;

    @Schema(description = "业务单号（订单号/售后单号）")
    private String bizId;

    @Schema(description = "模板变量：如 {\"orderNo\":\"SO...\",\"amount\":499900}")
    private Map<String, Object> vars;
}
