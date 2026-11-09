package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拼团成员（V8）：uk(team_id, member_id, deleted) 防重复参团。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_group_member")
public class MallGroupMember extends BaseEntity {

    /** 团ID */
    private Long teamId;

    /** 会员ID */
    private Long memberId;

    /** 关联订单ID（下单成功后有值） */
    private Long orderId;

    /** 是否团长：0否1是 */
    private Integer isLeader;
}
