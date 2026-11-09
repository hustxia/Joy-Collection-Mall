package com.mall.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 领券记录保存 DTO（新增/修改）
 */
@Data
public class MallCouponRecordSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotNull(message = "券ID 关联mall_coupon不能为空")
    private Long couponId;
    @NotNull(message = "会员ID不能为空")
    private Long memberId;
    private Long orderId;
    @NotNull(message = "使用状态不能为空")
    private Integer status;
    @NotNull(message = "领取时间不能为空")
    private LocalDateTime receivedTime;
    private LocalDateTime usedTime;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}