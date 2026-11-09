package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单精简视图（订单域 → 售后域 内部传输）
 */
@Data
public class OrderBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long memberId;
    /** V10 商家归属：1=平台自营店铺（退款冲减时定位商家应收） */
    private Long merchantId;
    /** 订单状态：10 待付款 / 20 待发货 / 30 已发货 / 40 已完成 / 50 已取消 / 60 已关闭 */
    private Integer status;
    private Long payAmount;
    private String payOrderNo;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
}
