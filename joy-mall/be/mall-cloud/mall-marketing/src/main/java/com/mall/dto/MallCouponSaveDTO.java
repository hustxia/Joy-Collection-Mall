package com.mall.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 优惠券保存 DTO（新增/修改）
 */
@Data
public class MallCouponSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotBlank(message = "券名称不能为空") @Size(max = 50, message = "券名称长度不能超过50")
    private String name;
    @NotNull(message = "类型不能为空")
    private Integer type;
    @NotNull(message = "面额不能为空")
    private Long amount;
    private Integer discountRate;
    @NotNull(message = "使用门槛不能为空")
    private Long thresholdAmount;
    @NotNull(message = "发放总量不能为空")
    private Integer totalCount;
    @NotNull(message = "已领取数不能为空")
    private Integer receivedCount;
    @NotNull(message = "每人限领不能为空")
    private Integer perLimit;
    @NotNull(message = "生效时间不能为空")
    private LocalDateTime startTime;
    @NotNull(message = "失效时间不能为空")
    private LocalDateTime endTime;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}