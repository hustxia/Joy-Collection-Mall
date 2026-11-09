package com.mall.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 收货地址保存 DTO（新增/修改）
 */
@Data
public class MallAddressSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotNull(message = "会员ID 关联mall_member不能为空")
    private Long memberId;
    @NotBlank(message = "收货人不能为空") @Size(max = 50, message = "收货人长度不能超过50")
    private String receiverName;
    @NotBlank(message = "联系电话不能为空") @Size(max = 20, message = "联系电话长度不能超过20")
    private String receiverPhone;
    @NotBlank(message = "省不能为空") @Size(max = 50, message = "省长度不能超过50")
    private String province;
    @NotBlank(message = "市不能为空") @Size(max = 50, message = "市长度不能超过50")
    private String city;
    @NotBlank(message = "区不能为空") @Size(max = 50, message = "区长度不能超过50")
    private String district;
    @NotBlank(message = "详细地址不能为空") @Size(max = 255, message = "详细地址长度不能超过255")
    private String detail;
    @NotNull(message = "默认地址不能为空")
    private Integer isDefault;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}