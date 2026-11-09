package com.mall.dto;

import lombok.Data;

/**
 * 提现审核入参
 */
@Data
public class WithdrawalAuditDTO {

    private Long id;

    /** true=通过并打款，false=拒绝 */
    private Boolean pass;

    private String rejectReason;
}
