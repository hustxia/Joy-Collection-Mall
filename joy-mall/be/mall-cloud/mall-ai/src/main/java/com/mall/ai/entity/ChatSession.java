package com.mall.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 客服会话（V16）。
 *
 * <p>状态机：0 机器人应答 →（转人工）1 已转人工 →（任一方结束/超时）2 已结束。
 * 转人工后 AI 停答，消息通道切到工单，{@code ticketId} 双向关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_chat_session")
public class ChatSession extends BaseEntity {

    public static final int STATUS_BOT = 0;
    public static final int STATUS_TRANSFERRED = 1;
    public static final int STATUS_CLOSED = 2;

    private Long memberId;

    /** 渠道：pc/h5 */
    private String channel;

    private Integer status;

    /** 进入来源：goods_detail/order_detail/home/float */
    private String source;

    /** 转人工生成的工单 ID */
    private Long ticketId;

    /** 满意度 1-5 */
    private Integer satisfaction;

    /** 最近一次识别意图 */
    private String intent;

    private Integer msgCount;

    private LocalDateTime lastActiveTime;
}
