package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 售后单精简视图（售后域 → 订单域 内部传输）：订单列表/详情展示最新售后角标。
 */
@Data
public class RefundBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String refundNo;
    private Long orderId;
    /** 售后状态：1 申请中 / 2 审核通过 / 3 已退款 / 4 已拒绝 / 5 已关闭 */
    private Integer status;
    private LocalDateTime refundTime;
}
