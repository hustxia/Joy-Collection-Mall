package com.mall.dto;

import lombok.Data;

/**
 * C 端确认页预览入参（不落库）。
 */
@Data
public class ShopConfirmDTO {

    /** 立即购买传 SKU；为空则按购物车勾选项 */
    private Long skuId;

    private Integer num;

    /** V12：收货地址ID（可选）：用于按地区匹配运费规则 */
    private Long addressId;
}
