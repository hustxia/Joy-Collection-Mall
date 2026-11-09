package com.mall.ai.support;

/** 意图分类结果（AI 客服只做这几类，避免自由发挥） */
public enum Intent {
    /** 明确要求人工 / 投诉 */
    HUMAN,
    /** 查订单、查进度 */
    ORDER,
    /** 物流、发货时效 */
    LOGISTICS,
    /** 退换货、退款进度 */
    REFUND,
    /** 政策/规则咨询（走 FAQ） */
    FAQ,
    /** 寒暄、商品咨询等 */
    CHITCHAT
}
