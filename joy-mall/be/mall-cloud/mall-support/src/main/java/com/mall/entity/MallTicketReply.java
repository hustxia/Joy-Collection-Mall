package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单回复（V14）：会员与客服共用一张表，靠 {@code replierType} 区分，
 * 会员端只展示 type=2 的客服回复 + 自己的留言（同一列表按时间顺序渲染即可）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_ticket_reply")
public class MallTicketReply extends BaseEntity {

    /** 回复人类型：会员 */
    public static final int REPLIER_MEMBER = 1;
    /** 回复人类型：客服 */
    public static final int REPLIER_AGENT = 2;

    /** 工单ID */
    private Long ticketId;

    /** 回复人类型：1会员2客服 */
    private Integer replierType;

    /** 回复人ID（会员ID 或后台用户ID） */
    private Long replierId;

    /** 回复内容 */
    private String content;
}
