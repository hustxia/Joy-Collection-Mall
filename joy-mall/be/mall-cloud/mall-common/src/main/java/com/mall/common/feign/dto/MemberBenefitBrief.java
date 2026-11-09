package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员权益快照（V13，会员域 → 订单域）。
 *
 * <p>订单域只需要三个数：折扣率、本月剩余包邮次数、等级名。成长值/权益文案属于 C 端展示，
 * 不进下单链路，避免每次下单都多传一堆用不上的字段。
 */
@Data
public class MemberBenefitBrief implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long memberId;

    private Long levelId;

    private String levelName;

    /** 成长值 */
    private Long growth;

    /** 会员折扣率：100=不打折，95=9.5折 */
    private Integer discountRate;

    /** 本月剩余包邮次数（0=无） */
    private Integer freeShippingRemain;

    /** 下一等级名称（已最高级为空） */
    private String nextLevelName;

    /** 升级还差多少成长值（已最高级为 0） */
    private Long nextLevelGap;
}
