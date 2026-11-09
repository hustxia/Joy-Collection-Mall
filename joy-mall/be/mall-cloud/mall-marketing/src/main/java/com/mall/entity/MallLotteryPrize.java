package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 抽奖奖品（V8）。权重随机；stock=-1 不限量，>=0 限量（条件更新防超发）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_lottery_prize")
public class MallLotteryPrize extends BaseEntity {

    /** 抽奖活动ID */
    private Long activityId;

    /** 奖品类型：0积分1优惠券2实物3谢谢参与 */
    private Integer prizeType;

    /** 奖品名称 */
    private String name;

    /** 关联ID：积分=分值，优惠券=券ID，实物=商品ID，谢谢参与=NULL */
    private Long refId;

    /** 权重（越大越易中，0=永不可中） */
    private Integer weight;

    /** 库存：-1不限量，>=0限量 */
    private Integer stock;

    /** 展示顺序 */
    private Integer sort;
}
