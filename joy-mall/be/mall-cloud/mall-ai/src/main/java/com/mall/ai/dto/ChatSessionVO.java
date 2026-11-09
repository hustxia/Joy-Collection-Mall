package com.mall.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    private String id;
    private String memberId;
    private String channel;
    private Integer status;
    private String source;
    private String ticketId;
    private Integer satisfaction;
    private String intent;
    private Integer msgCount;
    private LocalDateTime lastActiveTime;
    private LocalDateTime createTime;
    /** 首条用户提问（列表摘要） */
    private String summary;
}
