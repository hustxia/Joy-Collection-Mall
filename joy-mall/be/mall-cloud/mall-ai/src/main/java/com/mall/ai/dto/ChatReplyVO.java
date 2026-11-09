package com.mall.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 一条 AI 回复（同步接口与 SSE done 事件共用的结构） */
@Data
public class ChatReplyVO {
    private String messageId;
    private String sessionId;
    private String role = "ai";
    private String msgType = "text";
    private String content;
    /** 结构化卡片：订单/商品/转人工 */
    private List<Map<String, Object>> cards = new ArrayList<>();
    private String intent;
    /** 是否兜底话术（无证据） */
    private boolean fallback;
    /** 是否可转人工（兜底或投诉意图时为 true） */
    private boolean canTransfer;
    /** 推荐追问（FAQ 快捷问题） */
    private List<String> suggestions = new ArrayList<>();
    private String provider;
    private int latencyMs;
    private int tokens;
    /** 取证轨迹（内部审计用，前端可忽略） */
    private String toolTrace;
}
