package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * SKU 精简视图（商品域 → 订单域 内部传输）
 */
@Data
public class SkuBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long goodsId;
    private String skuCode;
    private String specJson;
    private Long price;
    private Integer stock;
    private String image;
    private Integer status;
}
