package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀活动（V8）。库存在 Redis 预扣（Lua 原子）+ available_stock 乐观锁兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_seckill_activity")
public class MallSeckillActivity extends BaseEntity {

    /** 商品ID */
    private Long goodsId;

    /** SKU ID */
    private Long skuId;

    /** 原价（分） */
    private Long originPrice;

    /** 秒杀价（分） */
    private Long seckillPrice;

    /** 秒杀总库存 */
    private Integer totalStock;

    /** 剩余库存（乐观锁扣减字段） */
    private Integer availableStock;

    /** 每人限购件数 */
    private Integer perLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：0未开始1进行中2已结束 */
    private Integer status;
}
