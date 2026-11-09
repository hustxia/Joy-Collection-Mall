package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车（V3 手写，生成器未纳管）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_cart")
public class MallCart extends BaseEntity {

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * SKU ID 关联mall_sku
     */
    private Long skuId;

    /**
     * 数量
     */
    private Integer num;

    /**
     * 勾选:1勾选0未勾选(下单页用)
     */
    private Integer checked;

    /**
     * 备注
     */
    private String remark;
}
