package com.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品SKU保存 DTO（新增/修改）
 */
@Data
public class MallSkuSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotNull(message = "商品ID 关联mall_goods不能为空")
    private Long goodsId;
    @NotBlank(message = "SKU编码不能为空") @Size(max = 32, message = "SKU编码长度不能超过32")
    private String skuCode;
    @Size(max = 500, message = "规格JSON数组文本长度不能超过500")
    private String specJson;
    @NotNull(message = "售价不能为空")
    private Long price;
    @NotNull(message = "库存不能为空")
    private Integer stock;
    @NotNull(message = "销量不能为空")
    private Integer sales;
    @Size(max = 500, message = "SKU图URL长度不能超过500")
    private String image;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V12 重量（克）：按重量计费依据，不传按 0 处理 */
    @Min(value = 0, message = "重量不能为负")
    private Integer weight;
}