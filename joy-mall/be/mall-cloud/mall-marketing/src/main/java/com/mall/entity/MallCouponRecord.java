package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 领券记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_coupon_record")
public class MallCouponRecord extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 券ID 关联mall_coupon
     */
    private Long couponId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 使用的订单ID 空=未使用
     */
    private Long orderId;

    /**
     * 使用状态:1未使用2已使用3已过期
     */
    private Integer status;

    /**
     * 领取时间
     */
    private LocalDateTime receivedTime;

    /**
     * 使用时间
     */
    private LocalDateTime usedTime;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}