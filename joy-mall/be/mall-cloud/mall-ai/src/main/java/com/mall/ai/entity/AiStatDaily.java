package com.mall.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * AI 客服日统计（V16）：会话/消息/转人工/兜底/token，成本看板数据源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_ai_stat_daily")
public class AiStatDaily extends BaseEntity {

    private LocalDate statDate;

    private Integer sessionCount;

    private Integer messageCount;

    private Integer aiReplyCount;

    private Integer transferCount;

    private Integer fallbackCount;

    private Long tokens;
}
