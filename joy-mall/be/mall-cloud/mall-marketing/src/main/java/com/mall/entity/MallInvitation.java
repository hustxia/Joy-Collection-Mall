package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 邀请关系
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_invitation")
public class MallInvitation extends BaseEntity {

    /** 邀请人（分销员）ID */
    private Long inviterId;

    /** 被邀请人（会员）ID */
    private Long inviteeId;

    /** 来源渠道：link/code/poster */
    private String channel;

    /** 绑定时间 */
    private LocalDateTime bindTime;

    /** 贡献订单数 */
    private Integer orderCount;

    /** 贡献成交额（分） */
    private Long contribution;

    /** 第几级：1一级 */
    private Integer level;
}
