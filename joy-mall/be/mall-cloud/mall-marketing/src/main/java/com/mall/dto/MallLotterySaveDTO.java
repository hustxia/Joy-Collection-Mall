package com.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抽奖活动保存 DTO（新增/修改共用）
 */
@Data
public class MallLotterySaveDTO {

    /** 修改时必传 */
    private Long id;

    @NotBlank(message = "活动名称必填")
    private String name;

    @NotNull(message = "每日限次必填")
    private Integer dailyLimit;

    /** 每次消耗积分，缺省 0=免费 */
    private Integer consumePoints;

    @NotNull(message = "开始时间必填")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间必填")
    private LocalDateTime endTime;

    /** 状态：0未开始1进行中2已结束，缺省 0 */
    private Integer status;
}
