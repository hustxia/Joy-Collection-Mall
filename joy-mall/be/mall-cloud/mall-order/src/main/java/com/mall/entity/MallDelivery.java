package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发货单（V12）：一个订单可以有多个包裹，每个包裹一张发货单。
 *
 * <p>与订单的关系：订单状态只在**所有明细发完**后才转 30 已发货；
 * 部分发货期间订单仍是 20，但明细上的 {@code shipped_quantity} 已递增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_delivery")
public class MallDelivery extends BaseEntity {

    /** 状态：已发货 */
    public static final int ST_SHIPPED = 1;
    /** 状态：已签收 */
    public static final int ST_SIGNED = 2;

    private String deliveryNo;

    private Long orderId;

    private String orderNo;

    private Long merchantId;

    private String expressCompany;

    private String expressNo;

    private Integer status;

    private LocalDateTime deliveryTime;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;
}
