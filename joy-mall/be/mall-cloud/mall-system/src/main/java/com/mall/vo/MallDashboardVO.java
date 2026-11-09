package com.mall.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 经营看板 VO（V4 P0-2）
 * 所有金额字段单位均为「分」，前端展示除以 100。
 */
@Data
public class MallDashboardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计时间范围：today / 7d / 30d */
    private String range;

    /** 成交金额 GMV（分）：已支付订单实付金额之和 */
    private Long gmv;

    /** 已支付订单数 */
    private Long orderCount;

    /** 客单价（分）：gmv / orderCount，订单数为 0 时为 0 */
    private Long avgOrderAmount;

    /** 退款金额（分）：已退款售后单金额之和 */
    private Long refundAmount;

    /** 售后率（0~1）：已退款单数 / 已支付订单数，分母为 0 时为 0 */
    private Double refundRate;

    /** 待办-待发货订单数（status=20，全量 backlog） */
    private Long pendingShipment;

    /** 待办-待审核售后数（refund status=1，全量 backlog） */
    private Long pendingRefundAudit;

    /** 新增会员数（统计区间内注册） */
    private Long newMembers;

    /** Top5 商品（按成交量降序） */
    private List<TopGoodsItem> topGoods;

    @Data
    public static class TopGoodsItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long goodsId;
        private String goodsName;
        private Long quantity;
        private Long amount; // 分
    }
}
