package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 抽奖活动（V8）。每人每日限次（daily_limit）+ 每次消耗积分（consume_points，0=免费）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_lottery_activity")
public class MallLotteryActivity extends BaseEntity {

    /** 活动名称 */
    private String name;

    /** 每人每日可抽次数 */
    private Integer dailyLimit;

    /** 每次抽奖消耗积分（0=免费） */
    private Integer consumePoints;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：0未开始1进行中2已结束 */
    private Integer status;
}
