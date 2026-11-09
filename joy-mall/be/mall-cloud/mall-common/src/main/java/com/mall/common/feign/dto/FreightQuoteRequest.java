package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 运费试算请求（订单域 → 商品域，V12）。
 *
 * <p>按「一个店铺一次」调用：跨店拆单时每个商家各算一次，各订单只承担自己店铺的运费。
 * 计费明细（件数/重量）由商品域按 skuId 自行取重量，订单域不感知商品属性。
 */
@Data
public class FreightQuoteRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家ID（1=平台自营）：用于取店铺默认运费模板 */
    private Long merchantId;

    /** 收货省（与规则 region_codes 匹配） */
    private String province;

    /** 收货市（省级规则未命中时再按市匹配） */
    private String city;

    /** 本店铺商品金额（分）：满额包邮判定用 */
    private Long goodsAmount;

    /** 计费明细：只传 skuId 与数量，重量由商品域补 */
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long skuId;
        private Long goodsId;
        private Integer quantity;
    }
}
