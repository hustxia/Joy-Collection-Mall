package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 促销活动命中留痕（V13）：下单时按订单写一行，便于对账与投诉排查。
 *
 * <p>只在订单创建成功后写入；活动过期/未命中的订单不写（验收 REQ-P1-01 第 2 条）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_promotion_hit")
public class MallPromotionHit extends BaseEntity {

    /** 活动ID */
    private Long promotionId;

    /** 活动名称（冗余） */
    private String promotionName;

    /** 订单ID */
    private Long orderId;

    /** 会员ID */
    private Long memberId;

    /** 该活动在本单的优惠金额（分） */
    private Long discountAmount;

    /** 命中时间 */
    private LocalDateTime hitTime;
}
