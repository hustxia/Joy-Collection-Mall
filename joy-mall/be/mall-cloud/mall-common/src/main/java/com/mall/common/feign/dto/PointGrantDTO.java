package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 积分发放/扣减请求（订单/售后域 → 会员域 内部传输，V7）
 * <p>changeType 1消费返 2签到 3下单抵扣 4退款扣回 5抽奖消耗 6抽奖奖励；points 正加负减。
 */
@Data
public class PointGrantDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long memberId;
    /** 1消费返 2签到 3下单抵扣 4退款扣回 5抽奖消耗 6抽奖奖励 */
    private Integer changeType;
    /** 正数加积分、负数扣积分（余额不足时会员域抛 400） */
    private Integer points;
    private Long orderId;
    private String remark;
}
