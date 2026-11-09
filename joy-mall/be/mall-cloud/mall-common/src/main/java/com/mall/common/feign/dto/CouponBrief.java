package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券精简视图（营销域 → 订单域 内部传输）
 */
@Data
public class CouponBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer type;
    private Long amount;
    private Integer discountRate;
    private Long thresholdAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 上架状态：1 上架 / 0 下架（可用券校验用） */
    private Integer status;
}
