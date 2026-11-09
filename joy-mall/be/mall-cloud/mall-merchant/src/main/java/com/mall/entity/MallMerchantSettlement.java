package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商家结算单（周期/手动聚合的单据凭证）。
 *
 * <p>金额均为分：goods_amount = 商家应收合计，platform_fee = 平台抽成合计，
 * refund_amount = 退款冲减合计，actual_amount = goods_amount - refund_amount。
 * <p>状态：0 待结算 / 1 已结算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant_settlement")
public class MallMerchantSettlement extends BaseEntity {

    private String settlementNo;

    private Long merchantId;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private Integer orderCount;

    private Long goodsAmount;

    private Long platformFee;

    private Long refundAmount;

    private Long actualAmount;

    private Integer status;

    private LocalDateTime settleTime;
}
