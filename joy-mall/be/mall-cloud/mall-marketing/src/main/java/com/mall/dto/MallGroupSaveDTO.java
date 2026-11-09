package com.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拼团活动保存 DTO（新增/修改共用）
 */
@Data
public class MallGroupSaveDTO {

    /** 修改时必传 */
    private Long id;

    @NotNull(message = "商品ID必填")
    private Long goodsId;

    @NotNull(message = "SKU ID必填")
    private Long skuId;

    @NotNull(message = "原价必填")
    private Long originPrice;

    @NotNull(message = "拼团价必填")
    private Long groupPrice;

    @NotNull(message = "成团人数必填")
    private Integer groupSize;

    @NotNull(message = "成团时限必填")
    private Integer validHours;

    @NotNull(message = "开始时间必填")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间必填")
    private LocalDateTime endTime;

    /** 状态：0未开始1进行中2已结束，缺省 0 */
    private Integer status;
}
