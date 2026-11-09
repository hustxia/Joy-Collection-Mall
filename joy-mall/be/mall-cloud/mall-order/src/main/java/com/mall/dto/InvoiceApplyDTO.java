package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发票申请入参（V12）：金额由服务端取订单实付，前端传了也不采纳。
 */
@Data
public class InvoiceApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;

    /** 1 个人 / 2 企业 */
    private Integer titleType;

    private String title;

    /** 纳税人识别号：企业抬头必填 */
    private String taxNo;

    private String email;
}
