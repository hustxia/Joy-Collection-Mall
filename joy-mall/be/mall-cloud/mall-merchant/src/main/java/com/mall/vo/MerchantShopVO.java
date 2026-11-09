package com.mall.vo;

import lombok.Data;

/**
 * C 端店铺视图（PC/H5 店铺首页用）：只暴露买家可见字段。
 */
@Data
public class MerchantShopVO {

    private Long id;

    private String merchantNo;

    /** 店铺名 */
    private String name;

    private String logo;

    private String intro;

    private String category;
}
