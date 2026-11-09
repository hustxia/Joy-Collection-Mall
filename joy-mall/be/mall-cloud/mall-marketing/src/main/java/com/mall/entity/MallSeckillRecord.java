package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀参与记录（V8）：uk(member_id, activity_id, deleted) 防重复购买。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_seckill_record")
public class MallSeckillRecord extends BaseEntity {

    /** 秒杀活动ID */
    private Long activityId;

    /** 会员ID */
    private Long memberId;

    /** 关联订单ID（下单成功后有值） */
    private Long orderId;

    /** SKU ID */
    private Long skuId;
}
