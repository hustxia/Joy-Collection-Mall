package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 促销活动作用范围明细（V13）：类目/商品/店铺各一行，一个活动挂多行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_promotion_scope")
public class MallPromotionScope extends BaseEntity {

    /** 活动ID */
    private Long promotionId;

    /** 范围类型：2类目3商品4店铺（与主表 scope_type 一致） */
    private Integer scopeType;

    /** 范围对象ID */
    private Long refId;
}
