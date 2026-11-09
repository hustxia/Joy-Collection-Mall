package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单回复入参（V14）：会员与客服共用，回复人身份只认会话，不接受前端传入
 */
@Data
@Schema(description = "工单回复入参")
public class TicketReplyDTO {

    @Schema(description = "回复内容")
    private String content;
}
