package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 佣金记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_commission_record")
public class MallCommissionRecord extends BaseEntity {

    /** 分销员ID */
    private Long distributorId;

    /** 关联订单ID（幂等键） */
    private Long orderId;

    /** 下单会员ID */
    private Long memberId;

    /** 商品ID */
    private Long goodsId;

    /** 佣金金额（分） */
    private Long amount;

    /** 佣金比例（‰，记录时快照） */
    private Integer rate;

    /** 状态：0待结算1已结算2失效 */
    private Integer status;

    /** 结算时间 */
    private LocalDateTime settleTime;
}
