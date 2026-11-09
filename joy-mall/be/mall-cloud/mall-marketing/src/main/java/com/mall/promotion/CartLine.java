package com.mall.promotion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 试算购物车行（V13 计算器输入模型）：只有算价需要的字段，不含商品名/图片等展示信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartLine {

    /** SKU ID */
    private Long skuId;

    /** 商品 ID */
    private Long goodsId;

    /** 类目 ID（类目活动匹配用，可空） */
    private Long categoryId;

    /** 店铺 ID（店铺活动匹配用，可空） */
    private Long merchantId;

    /** 单价（分） */
    private long price;

    /** 数量 */
    private int quantity;

    /** 原价小计（分） */
    public long originalAmount() {
        return price * quantity;
    }
}
