package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 品牌保存 DTO（新增/修改）
 */
@Data
public class MallBrandSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotBlank(message = "品牌名称不能为空") @Size(max = 50, message = "品牌名称长度不能超过50")
    private String name;
    @Size(max = 255, message = "品牌LOGO URL长度不能超过255")
    private String logo;
    @Size(max = 500, message = "品牌简介长度不能超过500")
    private String intro;
    @NotNull(message = "排序不能为空")
    private Integer sort;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}