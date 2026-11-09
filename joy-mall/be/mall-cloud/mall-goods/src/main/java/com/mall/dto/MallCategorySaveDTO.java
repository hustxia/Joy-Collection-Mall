package com.mall.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品类目保存 DTO（新增/修改）
 */
@Data
public class MallCategorySaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotNull(message = "父类目ID 0=顶级不能为空")
    private Long parentId;
    @NotBlank(message = "类目名称不能为空") @Size(max = 50, message = "类目名称长度不能超过50")
    private String name;
    @NotNull(message = "层级不能为空")
    private Integer level;
    @Size(max = 255, message = "类目图标URL长度不能超过255")
    private String icon;
    @NotNull(message = "排序 越小越靠前不能为空")
    private Integer sort;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}