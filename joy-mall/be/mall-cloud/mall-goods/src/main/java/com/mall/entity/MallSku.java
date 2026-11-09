package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品SKU
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_sku")
public class MallSku extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 商品ID 关联mall_goods
     */
    private Long goodsId;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 规格JSON数组文本
     */
    private String specJson;

    /**
     * 售价(分)
     */
    private Long price;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 销量(冗余)
     */
    private Integer sales;

    /**
     * SKU图URL
     */
    private String image;

    /**
     * 状态:1启用0停用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /**
     * 重量（克）。V12 新增列，见 db/v12-trade.sql：按重量计费的唯一数据来源，
     * 0 表示未维护（按重量模板下会被当作 0 克处理，运费只收首费）。
     */
    private Integer weight;
}