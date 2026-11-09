package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发票分页入参（V12）
 */
@Data
public class InvoicePageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号 / 发票申请单号 / 抬头 模糊 */
    private String keyword;

    private Integer status;

    private long pageNum = 1;

    private long pageSize = 10;
}
