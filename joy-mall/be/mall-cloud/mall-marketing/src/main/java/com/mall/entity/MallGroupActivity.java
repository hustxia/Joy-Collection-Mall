package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拼团活动（V8）。开团/参团即按拼团价建单（order_type=2），
 * 成团状态机由团（mall_group_team）承载：0拼团中 → 1已成团 / 2已失败。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_group_activity")
public class MallGroupActivity extends BaseEntity {

    /** 商品ID */
    private Long goodsId;

    /** SKU ID */
    private Long skuId;

    /** 原价（分） */
    private Long originPrice;

    /** 拼团价（分） */
    private Long groupPrice;

    /** 成团人数 */
    private Integer groupSize;

    /** 成团时限（小时） */
    private Integer validHours;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：0未开始1进行中2已结束 */
    private Integer status;
}
