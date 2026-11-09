package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单明细精简视图（订单域 → 售后域 内部传输）
 */
@Data
public class OrderItemBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long orderId;
    private Long goodsId;
    private Long skuId;
    private String goodsName;
    private String skuImage;
    private String specJson;
    private Long price;
    private Integer quantity;
    private Long totalAmount;
}
