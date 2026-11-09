package com.mall.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品 VO
 */
@Data
public class MallGoodsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
    private String goodsNo;
    private Long categoryId;
    private Long brandId;
    private String name;
    private String subTitle;
    private String mainImage;
    private String sliderImages;
    private String detail;
    private Long priceMin;
    private Long priceMax;
    private Integer sales;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V10 商家归属：1=平台自营店铺（历史数据全量回填 1） */
    private Long merchantId;

    /** V12 运费模板ID：空则回落店铺/全局默认 */
    private Long freightTemplateId;
}