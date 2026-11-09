package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券领取记录精简视图（营销域 → 订单域 内部传输）
 */
@Data
public class CouponRecordBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long couponId;
    private Long memberId;
    private Long orderId;
    private Integer status;
    private LocalDateTime receivedTime;
    private LocalDateTime usedTime;
}
