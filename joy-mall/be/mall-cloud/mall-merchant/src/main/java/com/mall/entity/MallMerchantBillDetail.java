package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家结算明细：一单一商家一条，唯一键 (order_id, merchant_id, deleted) 保证结算幂等。
 *
 * <p>订单完成时落一条「待出账」（settlement_id 为空）并累加商家余额；
 * 后台生成结算单时把待出账明细归集到 settlement_id 下。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant_bill_detail")
public class MallMerchantBillDetail extends BaseEntity {

    /** 结算单 ID：null = 待出账 */
    private Long settlementId;

    private Long orderId;

    private String orderNo;

    private Long merchantId;

    /** 结算时抽成费率快照（‰） */
    private Integer settleRate;

    /** 订单实付快照（分） */
    private Long payAmount;

    /** 商家应收（实付 - 平台抽成，分） */
    private Long goodsAmount;

    /** 平台抽成（分） */
    private Long platformFee;

    /** 退款冲减标记：0 否 / 1 是 */
    private Integer refundFlag;

    /** 已冲减的商家应收（分） */
    private Long refundAmount;
}
