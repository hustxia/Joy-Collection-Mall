package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工单状态变更入参（V14）
 */
@Data
@Schema(description = "工单状态变更入参")
public class TicketStatusDTO {

    @Schema(description = "状态：1待处理2已回复3已关闭")
    private Integer status;

    @Schema(description = "备注（暂只落库日志，不写工单表）")
    private String remark;
}
