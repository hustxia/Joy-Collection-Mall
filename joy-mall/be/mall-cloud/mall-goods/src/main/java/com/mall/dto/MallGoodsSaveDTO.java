package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品保存 DTO（新增/修改）
 */
@Data
public class MallGoodsSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotBlank(message = "商品编码不能为空") @Size(max = 32, message = "商品编码长度不能超过32")
    private String goodsNo;
    @NotNull(message = "类目ID 关联mall_category不能为空")
    private Long categoryId;
    private Long brandId;
    @NotBlank(message = "商品名称不能为空") @Size(max = 128, message = "商品名称长度不能超过128")
    private String name;
    @Size(max = 255, message = "副标题长度不能超过255")
    private String subTitle;
    @Size(max = 500, message = "主图URL长度不能超过500")
    private String mainImage;
    @Size(max = 2000, message = "轮播图URL JSON数组长度不能超过2000")
    private String sliderImages;
    private String detail;
    @NotNull(message = "最低价不能为空")
    private Long priceMin;
    @NotNull(message = "最高价不能为空")
    private Long priceMax;
    @NotNull(message = "销量不能为空")
    private Integer sales;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V10 商家归属：为空时由 Service 落 1（平台自营店铺） */
    private Long merchantId;

    /** V12 运费模板ID：为空则回落店铺默认 → 全局默认 */
    private Long freightTemplateId;
}