package com.mall.marketing;

import com.mall.promotion.CartLine;
import com.mall.promotion.PromotionCalculator;
import com.mall.promotion.PromotionResult;
import com.mall.promotion.PromotionRule;
import com.mall.promotion.PromotionTypes;
import com.mall.promotion.StackMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V13 促销引擎纯单测（REQ-P1-01）。
 *
 * <p>不启动 Spring、不连库、不读系统时间：{@link PromotionCalculator} 是纯函数，
 * 所以这里可以把验收标准里的四条逐条钉死，还能跑 100 次随机参数验证「试算与下单同源一致」。
 *
 * <p>金额单位一律「分」：300 元 = 30000 分。
 */
class PromotionCalcTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 10, 0, 0);

    // ===== 夹具 =====

    /** 满 300 减 50 */
    private static PromotionRule fullCut(long id, long threshold, long discount, int priority, boolean stackable) {
        PromotionRule rule = new PromotionRule();
        rule.setId(id);
        rule.setName("满" + threshold / 100 + "减" + discount / 100);
        rule.setType(PromotionTypes.FULL_CUT);
        rule.setScopeType(PromotionTypes.SCOPE_ALL);
        rule.setThreshold(threshold);
        rule.setDiscount(discount);
        rule.setPriority(priority);
        rule.setStackable(stackable);
        rule.setStartTime(NOW.minusDays(1));
        rule.setEndTime(NOW.plusDays(1));
        return rule;
    }

    /** 满 300 打 9 折（满折） */
    private static PromotionRule fullDiscount(long id, long threshold, int rate, int priority, boolean stackable) {
        PromotionRule rule = new PromotionRule();
        rule.setId(id);
        rule.setName("满" + threshold / 100 + "享" + rate / 10 + "折");
        rule.setType(PromotionTypes.FULL_DISCOUNT);
        rule.setScopeType(PromotionTypes.SCOPE_ALL);
        rule.setThreshold(threshold);
        rule.setDiscountRate(rate);
        rule.setPriority(priority);
        rule.setStackable(stackable);
        rule.setStartTime(NOW.minusDays(1));
        rule.setEndTime(NOW.plusDays(1));
        return rule;
    }

    private static CartLine line(long goodsId, long price, int qty) {
        return new CartLine(9000 + goodsId, goodsId, goodsId, 1L, price, qty);
    }

    // ===== 验收 1：叠加 vs 互斥 =====

    @Test
    @DisplayName("满300减50 + 9折：stack = 300*0.9-50 = 220，best = 减 50")
    void stackVersusBest() {
        List<PromotionRule> rules = List.of(
                fullCut(1L, 30000L, 5000L, 10, true),
                fullDiscount(2L, 30000L, 90, 20, true));
        List<CartLine> lines = List.of(line(1001L, 30000L, 1));

        PromotionResult stack = PromotionCalculator.calculate(rules, lines, NOW, StackMode.STACK);
        assertEquals(22000L, stack.getPayableAmount(), "叠加模式应付 = 300*0.9-50 = 220 元");
        assertEquals(8000L, stack.getPromotionDiscount(), "叠加模式优惠 = 30（9折）+ 50（满减）= 80 元");
        assertEquals(2, stack.getHits().size(), "两个可叠加活动都应命中");

        PromotionResult best = PromotionCalculator.calculate(rules, lines, NOW, StackMode.BEST);
        assertEquals(5000L, best.getPromotionDiscount(), "互斥模式只取最优：max(50, 30) = 50 元");
        assertEquals(25000L, best.getPayableAmount());
        assertEquals(1, best.getHits().size(), "互斥模式只能命中一个活动");
    }

    @Test
    @DisplayName("默认策略是 best（不传 mode 时互斥取最优）")
    void defaultModeIsBest() {
        List<PromotionRule> rules = List.of(
                fullCut(1L, 30000L, 5000L, 10, true),
                fullDiscount(2L, 30000L, 90, 20, true));
        PromotionResult result = PromotionCalculator.calculate(rules, List.of(line(1001L, 30000L, 1)), NOW, null);
        assertEquals("best", result.getStackMode());
        assertEquals(5000L, result.getPromotionDiscount());
    }

    @Test
    @DisplayName("满减门槛按原价判定：先打 9 折后仍按原价 300 判断「满 300」")
    void thresholdUsesOriginalAmount() {
        // 折后 270 < 300，但门槛看原价 300，满减必须照常生效，否则就是「打折把满减资格打没了」
        List<PromotionRule> rules = List.of(
                fullDiscount(1L, 0L, 90, 10, true),
                fullCut(2L, 30000L, 5000L, 20, true));
        PromotionResult result = PromotionCalculator.calculate(rules, List.of(line(1001L, 30000L, 1)),
                NOW, StackMode.STACK);
        assertEquals(22000L, result.getPayableAmount());
    }

    // ===== 验收 3：不为负 =====

    @Test
    @DisplayName("优惠大于应付时结果为 0，不为负")
    void discountNeverExceedsPayable() {
        // 满 3 元减 100 元，商品只要 5 元：减额必须被夹到 5 元，应付 0
        List<PromotionRule> rules = List.of(fullCut(1L, 300L, 10000L, 10, true));
        PromotionResult result = PromotionCalculator.calculate(rules, List.of(line(1001L, 500L, 1)),
                NOW, StackMode.BEST);
        assertEquals(0L, result.getPayableAmount(), "应付不能为负");
        assertEquals(500L, result.getPromotionDiscount(), "优惠最多抵到 0，不能超过商品金额");
        for (PromotionResult.LineResult line : result.getLines()) {
            assertTrue(line.getPayableAmount() >= 0, "行金额不能为负");
        }
    }

    @Test
    @DisplayName("多个大额活动叠加也不会把金额算成负数")
    void stackedDiscountsNeverGoNegative() {
        List<PromotionRule> rules = List.of(
                fullCut(1L, 0L, 900L, 10, true),
                fullCut(2L, 0L, 900L, 20, true),
                fullCut(3L, 0L, 900L, 30, true));
        PromotionResult result = PromotionCalculator.calculate(rules, List.of(line(1001L, 1000L, 1)),
                NOW, StackMode.STACK);
        assertEquals(0L, result.getPayableAmount());
        assertEquals(1000L, result.getPromotionDiscount());
    }

    // ===== 验收 2：过期不命中 =====

    @Test
    @DisplayName("活动过期/未开始都不命中，回落原价")
    void expiredPromotionNotApplied() {
        PromotionRule expired = fullCut(1L, 30000L, 5000L, 10, true);
        expired.setStartTime(NOW.minusDays(10));
        expired.setEndTime(NOW.minusSeconds(1));
        PromotionRule notStarted = fullCut(2L, 30000L, 5000L, 20, true);
        notStarted.setStartTime(NOW.plusSeconds(1));
        notStarted.setEndTime(NOW.plusDays(10));

        PromotionResult result = PromotionCalculator.calculate(List.of(expired, notStarted),
                List.of(line(1001L, 30000L, 1)), NOW, StackMode.STACK);
        assertEquals(0L, result.getPromotionDiscount(), "过期/未开始的活动不能算优惠");
        assertEquals(30000L, result.getPayableAmount(), "没有活动就是原价");
        assertTrue(result.getHits().isEmpty(), "没有命中就不能产生留痕");
    }

    // ===== 类型覆盖 =====

    @Test
    @DisplayName("第二件半价：买 3 件，只有 1 件按半价")
    void secondHalf() {
        PromotionRule rule = new PromotionRule();
        rule.setId(1L);
        rule.setType(PromotionTypes.SECOND_HALF);
        rule.setDiscountRate(50);
        rule.setStartTime(NOW.minusDays(1));
        PromotionResult result = PromotionCalculator.calculate(List.of(rule),
                List.of(line(1001L, 10000L, 3)), NOW, StackMode.BEST);
        assertEquals(5000L, result.getPromotionDiscount(), "3 件 = 1 对，第 2 件半价省 50 元");
        assertEquals(25000L, result.getPayableAmount());
    }

    @Test
    @DisplayName("套装：满 3 件 199 元，买 6 件省 2*(3*80-199)=82 元")
    void bundle() {
        PromotionRule rule = new PromotionRule();
        rule.setId(1L);
        rule.setType(PromotionTypes.BUNDLE);
        rule.setBundleQty(3);
        rule.setBundlePrice(19900L);
        rule.setStartTime(NOW.minusDays(1));
        PromotionResult result = PromotionCalculator.calculate(List.of(rule),
                List.of(line(1001L, 8000L, 6)), NOW, StackMode.BEST);
        assertEquals(8200L, result.getPromotionDiscount());
    }

    @Test
    @DisplayName("商品范围活动只作用于命中商品，未命中商品保持原价")
    void scopeGoodsOnly() {
        PromotionRule rule = fullCut(1L, 10000L, 2000L, 10, true);
        rule.setScopeType(PromotionTypes.SCOPE_GOODS);
        rule.getGoodsIds().add(1001L);
        List<CartLine> lines = List.of(line(1001L, 10000L, 1), line(2002L, 10000L, 1));
        PromotionResult result = PromotionCalculator.calculate(List.of(rule), lines, NOW, StackMode.BEST);
        assertEquals(2000L, result.getPromotionDiscount());
        assertEquals(8000L, result.getLines().get(0).getPayableAmount());
        assertEquals(10000L, result.getLines().get(1).getPayableAmount(), "非活动商品不打折");
    }

    // ===== 验收 4：100 次随机参数 =====

    @Test
    @DisplayName("100 次随机参数：活动顺序打乱结果不变、Σ行优惠=总优惠、应付不为负、best=单活动最优")
    void randomHundredCasesAreConsistent() {
        Random random = new Random(20260301L);
        for (int round = 0; round < 100; round++) {
            List<CartLine> lines = new ArrayList<>();
            int lineCount = 1 + random.nextInt(5);
            for (int i = 0; i < lineCount; i++) {
                long goodsId = 1000L + i;
                lines.add(new CartLine(9000L + i, goodsId, random.nextInt(3) + 1L,
                        random.nextInt(2) + 1L, 100 + random.nextInt(50000), 1 + random.nextInt(4)));
            }
            List<PromotionRule> rules = new ArrayList<>();
            int ruleCount = 1 + random.nextInt(4);
            for (int i = 0; i < ruleCount; i++) {
                long id = 100L + i;
                boolean stackable = random.nextBoolean();
                int priority = random.nextInt(50);
                switch (random.nextInt(5)) {
                    case 0 -> rules.add(fullCut(id, random.nextInt(5) * 10000L,
                            (long) random.nextInt(20000), priority, stackable));
                    case 1 -> rules.add(fullDiscount(id, random.nextInt(5) * 10000L,
                            50 + random.nextInt(51), priority, stackable));
                    case 2 -> {
                        PromotionRule r = new PromotionRule();
                        r.setId(id);
                        r.setType(PromotionTypes.LIMITED_DISCOUNT);
                        r.setDiscountRate(60 + random.nextInt(41));
                        r.setPriority(priority);
                        r.setStackable(stackable);
                        r.setStartTime(NOW.minusDays(1));
                        rules.add(r);
                    }
                    case 3 -> {
                        PromotionRule r = new PromotionRule();
                        r.setId(id);
                        r.setType(PromotionTypes.SECOND_HALF);
                        r.setDiscountRate(30 + random.nextInt(71));
                        r.setPriority(priority);
                        r.setStackable(stackable);
                        r.setStartTime(NOW.minusDays(1));
                        rules.add(r);
                    }
                    default -> {
                        PromotionRule r = new PromotionRule();
                        r.setId(id);
                        r.setType(PromotionTypes.BUNDLE);
                        r.setBundleQty(2 + random.nextInt(3));
                        r.setBundlePrice((long) random.nextInt(20000));
                        r.setPriority(priority);
                        r.setStackable(stackable);
                        r.setStartTime(NOW.minusDays(1));
                        rules.add(r);
                    }
                }
            }

            StackMode mode = random.nextBoolean() ? StackMode.BEST : StackMode.STACK;
            PromotionResult first = PromotionCalculator.calculate(rules, lines, NOW, mode);

            // 1) 活动顺序不影响结果（名单从库里查出来是 priority 排序，但接口传参顺序不可信）
            List<PromotionRule> shuffled = new ArrayList<>(rules);
            Collections.shuffle(shuffled, random);
            PromotionResult second = PromotionCalculator.calculate(shuffled, lines, NOW, mode);
            assertEquals(first.getPromotionDiscount(), second.getPromotionDiscount(),
                    "第 " + round + " 轮：活动顺序不能影响优惠金额");
            assertEquals(first.getPayableAmount(), second.getPayableAmount(),
                    "第 " + round + " 轮：活动顺序不能影响应付金额");

            // 2) 逐行优惠之和 == 总优惠（拆单对账前提）
            long lineSum = first.getLines().stream().mapToLong(PromotionResult.LineResult::getDiscountAmount).sum();
            assertEquals(first.getPromotionDiscount(), lineSum, "第 " + round + " 轮：行优惠之和必须等于总优惠");

            // 3) 应付 = max(总额-优惠, 0)，永不为负
            assertEquals(Math.max(first.getTotalAmount() - first.getPromotionDiscount(), 0L),
                    first.getPayableAmount(), "第 " + round + " 轮：应付口径错误");
            assertTrue(first.getPayableAmount() >= 0, "第 " + round + " 轮：应付不能为负");
            for (PromotionResult.LineResult line : first.getLines()) {
                assertTrue(line.getPayableAmount() >= 0, "第 " + round + " 轮：行金额不能为负");
                assertEquals(line.getOriginalAmount() - line.getDiscountAmount(), line.getPayableAmount(),
                        "第 " + round + " 轮：行恒等式 original - discount = payable");
            }
            // 4) 命中金额之和 == 总优惠（留痕金额与实付一致）
            long hitSum = first.getHits().stream().mapToLong(PromotionResult.HitResult::getAmount).sum();
            assertEquals(first.getPromotionDiscount(), hitSum, "第 " + round + " 轮：留痕金额之和必须等于总优惠");

            // 5) best 模式 == 各活动单独试算的最大值（"试算与下单同源"的等价表述）
            if (mode == StackMode.BEST) {
                long best = 0L;
                for (PromotionRule rule : rules) {
                    best = Math.max(best, PromotionCalculator
                            .calculate(List.of(rule), lines, NOW, StackMode.BEST).getPromotionDiscount());
                }
                assertEquals(best, first.getPromotionDiscount(), "第 " + round + " 轮：best 模式必须等于单活动最优");
                assertTrue(first.getHits().size() <= 1, "第 " + round + " 轮：best 模式至多命中 1 个活动");
            }
        }
    }
}
