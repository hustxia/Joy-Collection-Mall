package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 促销试算结果（V13，营销域 → 订单域）。
 *
 * <p>金额单位分。<b>不变式</b>：{@code payableAmount = max(totalAmount - promotionDiscount, 0)}，
 * 且 {@code promotionDiscount == Σ lines.discountAmount}——订单按店铺拆单时直接用逐行优惠归属各单，
 * 拆单后实付之和与整单试算严格相等。
 */
@Data
public class PromotionCalcResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 活动前商品总额（分） */
    private long totalAmount;

    /** 活动优惠总额（分） */
    private long promotionDiscount;

    /** 活动后应付（分，不含券/积分/运费） */
    private long payableAmount;

    /** 生效的叠加策略：best/stack */
    private String stackMode;

    /** 逐行结果 */
    private List<Line> lines = new ArrayList<>();

    /** 命中活动 */
    private List<Hit> hits = new ArrayList<>();

    /** 无活动命中时的说明（前端展示用） */
    private String tip;

    @Data
    public static class Line implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long skuId;
        private Long goodsId;
        private Long merchantId;
        private long originalAmount;
        private long discountAmount;
        private long payableAmount;
    }

    @Data
    public static class Hit implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long promotionId;
        private String promotionName;
        private Integer type;
        private String typeText;
        private String ruleText;
        private long amount;
        /** 逐行分摊：订单域按 merchantId 归集到各店铺订单 */
        private List<HitLine> lineDetails = new ArrayList<>();
    }

    @Data
    public static class HitLine implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long skuId;
        private Long goodsId;
        private Long merchantId;
        private long amount;
    }
}
