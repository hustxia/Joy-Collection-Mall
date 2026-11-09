package com.mall.dto;

import lombok.Data;

/**
 * 入驻审核入参
 */
@Data
public class MerchantAuditDTO {

    private Long id;

    /** true=通过，false=驳回 */
    private Boolean pass;

    /** 驳回原因：驳回必填 */
    private String rejectReason;
}
