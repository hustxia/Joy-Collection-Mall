package com.mall.ai.dto;

import lombok.Data;

@Data
public class ChatMessageDTO {
    private Long sessionId;
    private String content;
}
