package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_order_item")
public class MallOrderItem extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 订单ID 关联mall_order
     */
    private Long orderId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 商品名称(快照)
     */
    private String goodsName;

    /**
     * SKU图URL(快照)
     */
    private String skuImage;

    /**
     * 规格JSON(快照)
     */
    private String specJson;

    /**
     * 成交单价(分)
     */
    private Long price;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 小计(分)
     */
    private Long totalAmount;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /**
     * 商家归属（与所属订单一致）。V10 新增列，见 db/v10-merchant.sql。
     */
    private Long merchantId;

    /**
     * 已发货数量。V12 新增列，见 db/v12-trade.sql：支持一个订单项分多次发货，
     * 累计等于 {@code quantity} 时该行视为发完。
     */
    private Integer shippedQuantity;
}