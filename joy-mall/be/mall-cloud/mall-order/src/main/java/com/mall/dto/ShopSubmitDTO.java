package com.mall.dto;

import lombok.Data;

/**
 * C 端提交订单入参：立即购买（skuId+num）或购物车勾选（skuId 为空）。
 */
@Data
public class ShopSubmitDTO {

    private Long addressId;

    private Long skuId;

    private Integer num;

    private Long couponRecordId;

    private String buyerRemark;
}
