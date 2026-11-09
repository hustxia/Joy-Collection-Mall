package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推广商品
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_promotion_goods")
public class MallPromotionGoods extends BaseEntity {

    /** 商品ID */
    private Long goodsId;

    /** 推广该商品的分销员ID */
    private Long distributorId;

    /** 推广价（分） */
    private Long promoPrice;

    /** 佣金比例（‰） */
    private Integer commissionRate;

    /** 状态：1启用0停用 */
    private Integer status;
}
