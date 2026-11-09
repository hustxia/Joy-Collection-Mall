package com.mall.entity;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 抽奖记录（V8）。draw_date 为日限统计维度（member+activity+date 计数）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_lottery_record")
public class MallLotteryRecord extends BaseEntity {

    /** 会员ID */
    private Long memberId;

    /** 抽奖活动ID */
    private Long activityId;

    /** 奖品ID（降级且无谢谢参与配置时为空） */
    private Long prizeId;

    /** 是否中奖：0否1是 */
    private Integer isWin;

    /** 权益发放状态：0未发放1已发放 */
    private Integer receiveStatus;

    /** 实物奖品关联订单ID */
    private Long orderId;

    /** 抽奖日期（日限统计维度） */
    private LocalDate drawDate;
}
