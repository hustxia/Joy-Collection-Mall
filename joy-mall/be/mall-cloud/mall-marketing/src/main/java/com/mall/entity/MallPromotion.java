package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 促销活动（V13 促销引擎）。
 *
 * <p>与 {@code mall_coupon}（券）是两套东西：券是「领了才用」的会员资产，
 * 活动是「到点自动生效」的运营配置。下单链路上活动在前、券在后。
 *
 * <p>规则参数落在 {@code rule_json}（而不是拆成 6 个稀疏列），因为 6 种类型各自只用到 2~3 个字段，
 * 拆列会让表里长期存在大量恒为 0 的列，读的时候还得在 Java 里再拼一次「哪些字段对哪种类型有效」。
 * JSON 由 {@code PromotionRuleCodec} 解析成 {@link com.mall.promotion.PromotionRule}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_promotion")
public class MallPromotion extends BaseEntity {

    /** 活动名称 */
    private String name;

    /** 类型：1满减2满折3限时折扣4第二件半价5套装6加价购 */
    private Integer type;

    /** 作用范围：1全场2类目3商品4店铺 */
    private Integer scopeType;

    /** 规则JSON */
    private String ruleJson;

    /** 开始时间（空=不限） */
    private LocalDateTime startTime;

    /** 结束时间（空=不限） */
    private LocalDateTime endTime;

    /** 优先级：数值越小越优先 */
    private Integer priority;

    /** 是否可叠加：1是0否 */
    private Integer stackable;

    /** 状态：1上线0下线 */
    private Integer status;

    /** 备注 */
    private String remark;

    public static final int STATUS_ON = 1;
    public static final int STATUS_OFF = 0;
}
