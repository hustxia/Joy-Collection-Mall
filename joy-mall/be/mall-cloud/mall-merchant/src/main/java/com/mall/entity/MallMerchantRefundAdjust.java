package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家退款冲减流水：唯一键 (refund_id, deleted) 保证「同一售后单只冲减一次」。
 *
 * <p>退款完成要同时回退商家应收与平台抽成，按订单实付比例拆分：
 * merchant_share = refund_amount * goods_amount / pay_amount，platform_share = 差额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant_refund_adjust")
public class MallMerchantRefundAdjust extends BaseEntity {

    private Long refundId;

    private Long orderId;

    private Long merchantId;

    /** 本次退款金额（分） */
    private Long refundAmount;

    /** 本次冲减商家应收（分） */
    private Long merchantShare;

    /** 本次退回平台抽成（分） */
    private Long platformShare;
}
