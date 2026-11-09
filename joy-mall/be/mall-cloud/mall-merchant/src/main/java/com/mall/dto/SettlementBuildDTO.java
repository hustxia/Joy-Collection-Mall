package com.mall.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成结算单入参：期间为空时按待出账明细的实际时间范围自动取。
 */
@Data
public class SettlementBuildDTO {

    private Long merchantId;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;
}
