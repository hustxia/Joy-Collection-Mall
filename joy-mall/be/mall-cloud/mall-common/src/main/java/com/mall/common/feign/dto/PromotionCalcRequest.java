package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 促销试算请求（V13，订单域/商品域 → 营销域）。
 *
 * <p>订单域只负责把「行 + 单价 + 数量 + 类目/店铺归属」凑齐，活动匹配与算价全部回到营销域
 * {@code PromotionCalculator}，保证 <i>试算、确认页、下单</i> 三段同源（REQ-P1-01 验收第 4 条）。
 *
 * <p>时间不外传：营销域用自己的服务时间算活动窗口，避免调用方机器时钟偏差导致「预览有优惠、下单没有」。
 */
@Data
public class PromotionCalcRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会员ID（可空；会员等级折扣不在本接口范围内） */
    private Long memberId;

    /** 叠加策略覆盖：best/stack；空=营销域配置 */
    private String stackMode;

    /** 购物车行 */
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long skuId;
        private Long goodsId;
        private Long categoryId;
        private Long merchantId;
        /** 单价（分） */
        private Long price;
        private Integer quantity;
    }
}
