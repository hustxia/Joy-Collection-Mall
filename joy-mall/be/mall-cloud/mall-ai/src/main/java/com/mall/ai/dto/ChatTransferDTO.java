package com.mall.ai.dto;

import lombok.Data;

/** 转人工：reason 可空，会员点「转人工」时一般不带 */
@Data
public class ChatTransferDTO {
    private Long sessionId;
    private String reason;
}
