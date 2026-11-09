package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付流水分页入参（V12）
 */
@Data
public class PaymentPageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 支付单号 / 订单号 模糊 */
    private String keyword;

    private String channel;

    private Integer status;

    private long pageNum = 1;

    private long pageSize = 10;
}
