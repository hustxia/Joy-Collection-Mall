package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠券
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_coupon")
public class MallCoupon extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 券名称
     */
    private String name;

    /**
     * 类型:1满减券2折扣券
     */
    private Integer type;

    /**
     * 面额(分)
     */
    private Long amount;

    /**
     * 折扣率 如95=9.5折
     */
    private Integer discountRate;

    /**
     * 使用门槛(分) 0=无门槛
     */
    private Long thresholdAmount;

    /**
     * 发放总量
     */
    private Integer totalCount;

    /**
     * 已领取数
     */
    private Integer receivedCount;

    /**
     * 每人限领
     */
    private Integer perLimit;

    /**
     * 生效时间
     */
    private LocalDateTime startTime;

    /**
     * 失效时间
     */
    private LocalDateTime endTime;

    /**
     * 状态:1上架0下架
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}