package com.mall.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 客服消息（V16）。
 *
 * <p>{@code toolTrace} 记录本次回答用到的工具取证摘要（订单状态/物流节点），
 * 用于后台审计与「答案有没有依据」的追溯；不落 PII 明文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_chat_message")
public class ChatMessage extends BaseEntity {

    public static final String ROLE_USER = "user";
    public static final String ROLE_AI = "ai";
    public static final String ROLE_AGENT = "agent";
    public static final String ROLE_SYSTEM = "system";

    private Long sessionId;

    private Long memberId;

    private String role;

    /** text/order_card/goods_card/faq_suggest/transfer_card */
    private String msgType;

    private String content;

    /** 卡片 JSON 数组 */
    private String cards;

    private String intent;

    private String toolTrace;

    /** 1=兜底话术（无证据） */
    private Integer fallback;

    /** 实际使用的提供方：mock/openai/ollama/faq */
    private String provider;

    private Integer tokens;

    private Integer latencyMs;
}
