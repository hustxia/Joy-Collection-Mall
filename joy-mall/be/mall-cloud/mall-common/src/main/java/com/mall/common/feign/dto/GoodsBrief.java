package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品精简视图（商品域 → 订单域 内部传输）
 */
@Data
public class GoodsBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String mainImage;
    private Long priceMin;
    private Long priceMax;
    /** 上架状态：1 上架 / 0 下架（下单校验用） */
    private Integer status;
    /** V10 商家归属：1=平台自营店铺（下单按商家拆单用） */
    private Long merchantId;
    /** V13 类目归属：类目级促销活动匹配用（下单试算时随行下传） */
    private Long categoryId;
}
