package com.mall.promotion;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 促销试算结果（V13）。
 *
 * <p>不变式（单测断言）：
 * <ol>
 *   <li>{@code promotionDiscount == Σ lines.discountAmount}（分摊用最大余数法，不丢不多 1 分）；</li>
 *   <li>{@code payableAmount == max(totalAmount - promotionDiscount, 0)}，永不为负；</li>
 *   <li>同一入参、同一时间、同一策略，输出完全一致（试算与下单同源）。</li>
 * </ol>
 */
@Data
public class PromotionResult {

    /** 活动前商品总额（分） */
    private long totalAmount;

    /** 活动优惠总额（分） */
    private long promotionDiscount;

    /** 活动后应付（分，不含运费/券/积分） */
    private long payableAmount;

    /** 生效的叠加策略 */
    private String stackMode = StackMode.BEST.code();

    /** 逐行结果（顺序与入参一致） */
    private List<LineResult> lines = new ArrayList<>();

    /** 命中的活动明细 */
    private List<HitResult> hits = new ArrayList<>();

    /** 逐行活动价 */
    @Data
    public static class LineResult {
        private Long skuId;
        private Long goodsId;
        private Long merchantId;
        /** 原价小计（分） */
        private long originalAmount;
        /** 活动优惠（分） */
        private long discountAmount;
        /** 活动后小计（分） */
        private long payableAmount;
        /** 活动后单价（分，向下取整，仅展示用） */
        private long finalUnitPrice;
    }

    /** 命中活动（附逐行分摊明细，便于按店铺拆单后精确归属优惠金额） */
    @Data
    public static class HitResult {
        private Long promotionId;
        private String promotionName;
        private Integer type;
        private String typeText;
        private String ruleText;
        /** 本活动优惠总额（分） */
        private long amount;
        /** 逐行分摊明细 */
        private List<HitLine> lineDetails = new ArrayList<>();
    }

    /** 命中活动的单行分摊 */
    @Data
    public static class HitLine {
        private Long skuId;
        private Long goodsId;
        private Long merchantId;
        private long amount;

        public HitLine() {
        }

        public HitLine(Long skuId, Long goodsId, Long merchantId, long amount) {
            this.skuId = skuId;
            this.goodsId = goodsId;
            this.merchantId = merchantId;
            this.amount = amount;
        }
    }
}
