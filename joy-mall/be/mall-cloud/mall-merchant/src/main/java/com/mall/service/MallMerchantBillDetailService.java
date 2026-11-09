package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallMerchantBillDetail;

/**
 * 商家结算明细 + 订单完成入账 + 退款冲减。
 *
 * <p>两个写入口都<b>幂等</b>：结算按 (order_id, merchant_id) 唯一键，冲减按 refund_id 唯一键，
 * 重复事件直接返回 false，不重复加/扣余额。
 */
public interface MallMerchantBillDetailService extends IService<MallMerchantBillDetail> {

    /**
     * 订单完成入账：按商家费率抽成，商家应收计入 account_balance / settled_total。
     *
     * @return true=本次入账；false=已入账过或商家未启用（幂等跳过）
     */
    boolean settleOrder(Long orderId, Long merchantId, String orderNo, Long payAmount);

    /**
     * 退款冲减：按实付比例回退商家应收，同时回退平台抽成。
     *
     * @return true=本次冲减；false=无结算明细或该售后单已冲减过（幂等跳过）
     */
    boolean refundAdjust(Long refundId, Long orderId, Long merchantId, Long refundAmount);
}
