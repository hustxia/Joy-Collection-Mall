package com.mall.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单保存 DTO（新增/修改）
 */
@Data
public class MallOrderSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="dto-fields">
    /**
     * 主键，新增时为空
     */
    private Long id;

    @NotBlank(message = "订单号不能为空") @Size(max = 32, message = "订单号长度不能超过32")
    private String orderNo;
    @NotNull(message = "会员ID 关联mall_member不能为空")
    private Long memberId;
    @NotNull(message = "商品总额不能为空")
    private Long totalAmount;
    @NotNull(message = "运费不能为空")
    private Long freightAmount;
    @NotNull(message = "优惠金额不能为空")
    private Long discountAmount;
    @NotNull(message = "实付金额不能为空")
    private Long payAmount;
    @NotNull(message = "订单状态不能为空")
    private Integer status;
    @NotNull(message = "支付状态不能为空")
    private Integer payStatus;
    private Integer payType;
    @Size(max = 64, message = "支付单号 关联pay_order长度不能超过64")
    private String payOrderNo;
    private LocalDateTime payTime;
    @NotBlank(message = "收货人不能为空") @Size(max = 50, message = "收货人长度不能超过50")
    private String receiverName;
    @NotBlank(message = "联系电话不能为空") @Size(max = 20, message = "联系电话长度不能超过20")
    private String receiverPhone;
    @NotBlank(message = "收货地址不能为空") @Size(max = 300, message = "收货地址长度不能超过300")
    private String receiverAddress;
    @Size(max = 50, message = "快递公司长度不能超过50")
    private String expressCompany;
    @Size(max = 50, message = "快递单号长度不能超过50")
    private String expressNo;
    private LocalDateTime deliverTime;
    private LocalDateTime finishTime;
    @Size(max = 255, message = "买家留言长度不能超过255")
    private String buyerRemark;
    @Size(max = 255, message = "管理员备注长度不能超过255")
    private String remark;
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}