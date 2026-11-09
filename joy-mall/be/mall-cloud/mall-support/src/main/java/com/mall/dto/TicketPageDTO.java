package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台工单分页入参（V14）
 */
@Data
@Schema(description = "工单分页入参")
public class TicketPageDTO {

    @Schema(description = "页码")
    private long pageNum = 1;

    @Schema(description = "每页条数")
    private long pageSize = 10;

    @Schema(description = "状态：1待处理2已回复3已关闭")
    private Integer status;

    @Schema(description = "分类：1~5")
    private Integer category;

    @Schema(description = "关键字：标题/工单号/订单号")
    private String keyword;

    @Schema(description = "只看首响超时")
    private Boolean timeoutOnly;
}
