package com.mall.ai.dto;

import lombok.Data;

@Data
public class ChatFeedbackDTO {
    private Long messageId;
    /** up / down */
    private String rating;
    private String reason;
}
