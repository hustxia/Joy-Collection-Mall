package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分销员
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_distributor")
public class MallDistributor extends BaseEntity {

    /** 会员ID */
    private Long memberId;

    /** 专属邀请码 */
    private String inviteCode;

    /** 分销员等级：默认1 */
    private Integer level;

    /** 状态：0待审1启用2停用3驳回 */
    private Integer status;

    /** 上级分销员ID（一级为NULL） */
    private Long parentId;

    /** 累计佣金（分） */
    private Long totalCommission;

    /** 已结算佣金（分） */
    private Long settled;

    /** 可提现佣金（分） */
    private Long withdrawable;

    /** 驳回原因 */
    private String rejectReason;
}
