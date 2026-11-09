package com.mall.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 售后退款单保存 DTO（新增/修改）
 */
@Data
public class MallRefundSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotBlank(message = "售后单号不能为空") @Size(max = 32, message = "售后单号长度不能超过32")
    private String refundNo;
    @NotNull(message = "订单ID 关联mall_order不能为空")
    private Long orderId;
    @NotBlank(message = "订单号不能为空") @Size(max = 32, message = "订单号长度不能超过32")
    private String orderNo;
    @NotNull(message = "会员ID不能为空")
    private Long memberId;
    @NotNull(message = "售后类型不能为空")
    private Integer type;
    @NotNull(message = "退款金额不能为空")
    private Long amount;
    @Size(max = 255, message = "申请原因长度不能超过255")
    private String reason;
    @Size(max = 2000, message = "凭证图URL JSON数组长度不能超过2000")
    private String evidenceImages;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @Size(max = 255, message = "审核意见长度不能超过255")
    private String auditRemark;
    private LocalDateTime refundTime;
    @Size(max = 64, message = "原支付单号 关联pay_order长度不能超过64")
    private String payOrderNo;
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}