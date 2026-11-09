package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发货单明细（V12）：记录本次包裹里每个订单项发了多少件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_delivery_item")
public class MallDeliveryItem extends BaseEntity {

    private Long deliveryId;

    private Long orderId;

    private Long orderItemId;

    private Long skuId;

    private String goodsName;

    private Integer quantity;
}
