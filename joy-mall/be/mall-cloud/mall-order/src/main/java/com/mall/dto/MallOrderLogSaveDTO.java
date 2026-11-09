package com.mall.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单流转日志保存 DTO（新增/修改）
 */
@Data
public class MallOrderLogSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotNull(message = "订单ID 关联mall_order不能为空")
    private Long orderId;
    @NotBlank(message = "订单号不能为空") @Size(max = 32, message = "订单号长度不能超过32")
    private String orderNo;
    @NotBlank(message = "变更内容不能为空") @Size(max = 255, message = "变更内容长度不能超过255")
    private String content;
    @Size(max = 50, message = "操作人长度不能超过50")
    private String operator;
    @NotNull(message = "操作方不能为空")
    private Integer operatorType;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}