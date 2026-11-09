package com.mall.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ChatMessageVO {
    private String id;
    private String role;
    private String msgType;
    private String content;
    private List<Map<String, Object>> cards;
    private boolean fallback;
    private String provider;
    private LocalDateTime createTime;
}
