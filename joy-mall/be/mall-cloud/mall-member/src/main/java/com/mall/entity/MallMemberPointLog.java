package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员积分流水（V7 手写）：余额在 mall_member.points，流水用于对账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_member_point_log")
public class MallMemberPointLog extends BaseEntity {

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 变动类型:1消费返2签到3下单抵扣4退款扣回
     */
    private Integer changeType;

    /**
     * 变动积分：正加负减
     */
    private Integer points;

    /**
     * 变动后余额
     */
    private Integer balanceAfter;

    /**
     * 关联订单ID（消费返/抵扣/扣回时有值）
     */
    private Long orderId;

    /**
     * 备注
     */
    private String remark;
}
