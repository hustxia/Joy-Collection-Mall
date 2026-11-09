package com.mall.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单明细保存 DTO（新增/修改）
 */
@Data
public class MallOrderItemSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotNull(message = "订单ID 关联mall_order不能为空")
    private Long orderId;
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;
    @NotBlank(message = "商品名称不能为空") @Size(max = 128, message = "商品名称长度不能超过128")
    private String goodsName;
    @Size(max = 500, message = "SKU图URL长度不能超过500")
    private String skuImage;
    @Size(max = 500, message = "规格JSON长度不能超过500")
    private String specJson;
    @NotNull(message = "成交单价不能为空")
    private Long price;
    @NotNull(message = "购买数量不能为空")
    private Integer quantity;
    @NotNull(message = "小计不能为空")
    private Long totalAmount;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}