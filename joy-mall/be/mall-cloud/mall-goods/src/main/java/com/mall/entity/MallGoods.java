package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_goods")
public class MallGoods extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 商品编码
     */
    private String goodsNo;

    /**
     * 类目ID 关联mall_category
     */
    private Long categoryId;

    /**
     * 品牌ID 关联mall_brand
     */
    private Long brandId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 主图URL
     */
    private String mainImage;

    /**
     * 轮播图URL JSON数组
     */
    private String sliderImages;

    /**
     * 商品详情 富文本HTML
     */
    private String detail;

    /**
     * 最低价(分)
     */
    private Long priceMin;

    /**
     * 最高价(分)
     */
    private Long priceMax;

    /**
     * 销量(冗余)
     */
    private Integer sales;

    /**
     * 状态:1上架0下架2草稿
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /**
     * 商家归属：1=平台自营店铺。V10 新增列，见 db/v10-merchant.sql。
     */
    private Long merchantId;

    /**
     * 运费模板ID。V12 新增列，见 db/v12-trade.sql；空则回落「店铺默认模板 → 全局默认模板」。
     */
    private Long freightTemplateId;
}