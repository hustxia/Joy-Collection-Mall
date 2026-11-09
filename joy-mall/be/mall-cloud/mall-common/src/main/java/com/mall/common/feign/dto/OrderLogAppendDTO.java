package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 追加订单流转日志请求（售后域 → 订单域 内部传输）
 */
@Data
public class OrderLogAppendDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private String content;
    private String operator;
    /** 1 会员 / 2 管理员 / 3 系统 */
    private Integer operatorType;
}
