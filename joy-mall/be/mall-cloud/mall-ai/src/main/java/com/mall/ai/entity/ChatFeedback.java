package com.mall.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息级反馈（V16）：同一会员对同一消息只保留一条（唯一键），重复提交按覆盖处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_chat_feedback")
public class ChatFeedback extends BaseEntity {

    private Long messageId;

    private Long sessionId;

    private Long memberId;

    /** up/down */
    private String rating;

    private String reason;
}
