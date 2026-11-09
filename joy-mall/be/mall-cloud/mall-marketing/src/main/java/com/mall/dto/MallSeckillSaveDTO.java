package com.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动保存 DTO（新增/修改共用）
 */
@Data
public class MallSeckillSaveDTO {

    /** 修改时必传 */
    private Long id;

    @NotNull(message = "商品ID必填")
    private Long goodsId;

    @NotNull(message = "SKU ID必填")
    private Long skuId;

    @NotNull(message = "原价必填")
    private Long originPrice;

    @NotNull(message = "秒杀价必填")
    private Long seckillPrice;

    @NotNull(message = "秒杀总库存必填")
    private Integer totalStock;

    /** 每人限购件数，缺省 1 */
    private Integer perLimit;

    @NotNull(message = "开始时间必填")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间必填")
    private LocalDateTime endTime;

    /** 状态：0未开始1进行中2已结束，缺省 0 */
    private Integer status;
}
