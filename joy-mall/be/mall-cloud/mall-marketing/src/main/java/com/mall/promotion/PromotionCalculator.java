package com.mall.promotion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 促销计算引擎（V13 纯函数核心）。
 *
 * <p><b>为什么是纯函数</b>：需求 REQ-P1-01 验收第 4 条要求「同参数试算与下单结果一致（100 次随机抽样）」。
 * 唯一可靠的做法是让 C 端试算、订单确认页、订单提交、后台预览调用<i>同一段</i>代码，
 * 且这段代码不依赖 Spring 容器、不查库、不带随机数、不读系统时间（时间从入参传入）——
 * 于是它可以在毫秒级跑 100 次随机参数而不需要数据库。
 *
 * <p><b>计算口径</b>：
 * <ol>
 *   <li>门槛按「活动范围内商品<i>原价</i>小计」判定（先打折再满减时 270 元仍算「满 300」吗？不——
 *       门槛看原价 300，减额在余额 270 上扣，所以结果是 220，符合验收第 1 条）；</li>
 *   <li>比例类活动先算、定额类（满减）后算；同类内按 priority 升序、ID 升序，保证顺序确定；</li>
 *   <li>任何一步的减免都不超过当前余额，最终应付 clamp 到 0，永不为负；</li>
 *   <li>活动优惠按行用「最大余数法」分摊，Σ行优惠 == 活动优惠总额（不丢分、不多收）。</li>
 * </ol>
 *
 * <p>不做的事：券、积分、运费、会员折扣都不在这里算。它们属于「活动之后」的链路，
 * 由订单域按下单口径依次叠加（见需求文档 §八「关键依赖」）。
 */
public final class PromotionCalculator {

    private PromotionCalculator() {
    }

    /**
     * 试算。
     *
     * @param promotions 候选活动（可以包含已过期/未开始/下线的，内部会过滤）
     * @param lines      购物车行（顺序即输出顺序）
     * @param now        业务时间（订单域传下单时间，商品页传当前时间）
     * @param mode       叠加策略，null 视为 BEST
     */
    public static PromotionResult calculate(List<PromotionRule> promotions, List<CartLine> lines,
                                            LocalDateTime now, StackMode mode) {
        StackMode stackMode = mode == null ? StackMode.BEST : mode;
        LocalDateTime at = now == null ? LocalDateTime.now() : now;
        List<CartLine> valid = new ArrayList<>();
        long[] originals = new long[lines == null ? 0 : lines.size()];
        long total = 0L;
        for (int i = 0; i < originals.length; i++) {
            CartLine line = lines.get(i);
            long amount = line == null ? 0L : Math.max(line.originalAmount(), 0L);
            originals[i] = amount;
            total += amount;
            valid.add(line);
        }

        long[] amounts = originals.clone();
        List<PromotionResult.HitResult> hits = new ArrayList<>();

        List<PromotionRule> active = (promotions == null ? List.<PromotionRule>of() : promotions).stream()
                .filter(p -> p != null && p.activeAt(at))
                .sorted(Comparator.comparingInt((PromotionRule p) -> ord(p)).thenComparing(PromotionCalculator::idOf))
                .toList();

        if (stackMode == StackMode.BEST) {
            applyBest(active, valid, originals, amounts, hits);
        } else {
            applyStack(active, valid, originals, amounts, hits);
        }

        PromotionResult result = new PromotionResult();
        result.setStackMode(stackMode.code());
        result.setTotalAmount(total);
        long[] lineDiscounts = new long[originals.length];
        for (int i = 0; i < originals.length; i++) {
            lineDiscounts[i] = originals[i] - amounts[i];
        }
        long discount = 0L;
        for (int i = 0; i < originals.length; i++) {
            discount += lineDiscounts[i];
            PromotionResult.LineResult lr = new PromotionResult.LineResult();
            CartLine line = valid.get(i);
            lr.setSkuId(line == null ? null : line.getSkuId());
            lr.setGoodsId(line == null ? null : line.getGoodsId());
            lr.setMerchantId(line == null ? null : line.getMerchantId());
            lr.setOriginalAmount(originals[i]);
            lr.setDiscountAmount(lineDiscounts[i]);
            lr.setPayableAmount(amounts[i]);
            int qty = line == null || line.getQuantity() <= 0 ? 1 : line.getQuantity();
            lr.setFinalUnitPrice(amounts[i] / qty);
            result.getLines().add(lr);
        }
        result.setPromotionDiscount(discount);
        result.setPayableAmount(Math.max(total - discount, 0L));
        result.setHits(hits);
        return result;
    }

    // ===== 两种策略 =====

    /** 互斥取最优：每个活动各自单独算一遍，只采纳优惠最大的一个（并列取优先级高、ID 小） */
    private static void applyBest(List<PromotionRule> active, List<CartLine> lines, long[] originals,
                                  long[] amounts, List<PromotionResult.HitResult> hits) {
        PromotionRule bestRule = null;
        long[] bestPerLine = null;
        long bestAmount = 0L;
        for (PromotionRule rule : active) {
            long[] probe = originals.clone();
            long[] perLine = new long[originals.length];
            long amount = applyOne(rule, lines, originals, probe, perLine);
            if (amount <= 0) {
                continue;
            }
            if (amount > bestAmount) {
                bestAmount = amount;
                bestRule = rule;
                bestPerLine = perLine;
            }
        }
        if (bestRule == null) {
            return;
        }
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = originals[i] - bestPerLine[i];
        }
        hits.add(hitOf(bestRule, bestPerLine, lines, bestAmount));
    }

    /**
     * 叠加：可叠加活动全部生效（比例类先、定额类后），不可叠加活动在剩余余额上择优取 1 个。
     */
    private static void applyStack(List<PromotionRule> active, List<CartLine> lines, long[] originals,
                                   long[] amounts, List<PromotionResult.HitResult> hits) {
        List<PromotionRule> stackable = new ArrayList<>();
        List<PromotionRule> exclusive = new ArrayList<>();
        for (PromotionRule rule : active) {
            if (Boolean.TRUE.equals(rule.getStackable())) {
                stackable.add(rule);
            } else {
                exclusive.add(rule);
            }
        }
        stackable.sort(Comparator.comparingInt((PromotionRule p) -> phaseOf(p))
                .thenComparingInt(PromotionCalculator::ord)
                .thenComparing(PromotionCalculator::idOf));
        for (PromotionRule rule : stackable) {
            long[] perLine = new long[originals.length];
            long amount = applyOne(rule, lines, originals, amounts, perLine);
            if (amount > 0) {
                hits.add(hitOf(rule, perLine, lines, amount));
            }
        }
        // 不可叠加的互斥活动：在「已叠加后的余额」上各试一遍，取最优
        PromotionRule bestRule = null;
        long[] bestPerLine = null;
        long bestAmount = 0L;
        for (PromotionRule rule : exclusive) {
            long[] probe = amounts.clone();
            long[] perLine = new long[originals.length];
            long amount = applyOne(rule, lines, originals, probe, perLine);
            if (amount > bestAmount) {
                bestAmount = amount;
                bestRule = rule;
                bestPerLine = perLine;
            }
        }
        if (bestRule != null) {
            for (int i = 0; i < amounts.length; i++) {
                amounts[i] -= bestPerLine[i];
            }
            hits.add(hitOf(bestRule, bestPerLine, lines, bestAmount));
        }
    }

    // ===== 单个活动 =====

    /**
     * 对单个活动求值并就地扣减 {@code amounts}。
     *
     * @param originals 原价小计（门槛判定只看它，避免「先折后减」把门槛算低）
     * @param perLine   输出：逐行优惠金额
     * @return 本活动优惠总额（分）
     */
    private static long applyOne(PromotionRule rule, List<CartLine> lines, long[] originals,
                                 long[] amounts, long[] perLine) {
        int n = amounts.length;
        boolean[] matched = new boolean[n];
        long originalBase = 0L;
        long currentBase = 0L;
        for (int i = 0; i < n; i++) {
            CartLine line = lines.get(i);
            if (line == null || originals[i] <= 0 || !rule.matches(line)) {
                continue;
            }
            matched[i] = true;
            originalBase += originals[i];
            currentBase += amounts[i];
        }
        if (currentBase <= 0) {
            return 0L;
        }
        int type = rule.getType() == null ? 0 : rule.getType();
        long threshold = rule.getThreshold() == null ? 0L : Math.max(rule.getThreshold(), 0L);
        boolean thresholdOk = threshold <= 0 || originalBase >= threshold;

        switch (type) {
            case PromotionTypes.FULL_CUT -> {
                if (!thresholdOk) {
                    return 0L;
                }
                long d = Math.min(rule.getDiscount() == null ? 0L : Math.max(rule.getDiscount(), 0L), currentBase);
                return distribute(amounts, matched, d, perLine);
            }
            case PromotionTypes.FULL_DISCOUNT -> {
                if (!thresholdOk) {
                    return 0L;
                }
                long d = rateDiscount(currentBase, rule.getDiscountRate());
                return distribute(amounts, matched, d, perLine);
            }
            case PromotionTypes.LIMITED_DISCOUNT -> {
                long d = rateDiscount(currentBase, rule.getDiscountRate());
                return distribute(amounts, matched, d, perLine);
            }
            case PromotionTypes.SECOND_HALF, PromotionTypes.BUNDLE -> {
                long total = 0L;
                for (int i = 0; i < n; i++) {
                    if (!matched[i]) {
                        continue;
                    }
                    CartLine line = lines.get(i);
                    long d = type == PromotionTypes.SECOND_HALF
                            ? secondHalfDiscount(line, rule.getDiscountRate(), amounts[i])
                            : bundleDiscount(line, rule, amounts[i]);
                    d = Math.max(Math.min(d, amounts[i]), 0L);
                    perLine[i] = d;
                    amounts[i] -= d;
                    total += d;
                }
                return total;
            }
            case PromotionTypes.ADD_ON -> {
                // 加价购不改主商品价格（换购价在加购行单独计价），这里只算 0，命中与否由 available 接口展示
                return 0L;
            }
            default -> {
                return 0L;
            }
        }
    }

    /** 每满 2 件，第 2 件按折扣率（默认半价）计 */
    private static long secondHalfDiscount(CartLine line, Integer rate, long currentAmount) {
        int qty = line.getQuantity();
        int pairs = qty / 2;
        if (pairs <= 0) {
            return 0L;
        }
        int r = rate == null ? 50 : Math.max(Math.min(rate, 100), 0);
        long perItemDiscount = line.getPrice() * (100 - r) / 100;
        return Math.min(pairs * perItemDiscount, currentAmount);
    }

    /** 套装：每满 bundleQty 件按 bundlePrice 打包 */
    private static long bundleDiscount(CartLine line, PromotionRule rule, long currentAmount) {
        int qty = rule.getBundleQty() == null ? 0 : rule.getBundleQty();
        long bundlePrice = rule.getBundlePrice() == null ? 0L : Math.max(rule.getBundlePrice(), 0L);
        if (qty <= 0 || bundlePrice <= 0 || line.getQuantity() < qty) {
            return 0L;
        }
        int groups = line.getQuantity() / qty;
        long normal = (long) groups * qty * line.getPrice();
        long bundle = (long) groups * bundlePrice;
        return Math.min(Math.max(normal - bundle, 0L), currentAmount);
    }

    /** 折扣金额：向下取整到分，保证不会多减 */
    private static long rateDiscount(long base, Integer rate) {
        int r = rate == null ? 100 : Math.max(Math.min(rate, 100), 0);
        return base - base * r / 100;
    }

    // ===== 分摊 =====

    /**
     * 把 {@code discount} 按各行当前余额比例分摊到命中行（最大余数法），并把优惠就地扣掉。
     *
     * <p>为什么必须分摊：订单按店铺拆单，每个订单只承担本店商品的优惠；不分摊就只能整单抹平，
     * 拆单后各订单实付之和会与整单试算对不上（差 1 分也算对不上）。
     */
    private static long distribute(long[] amounts, boolean[] matched, long discount, long[] perLine) {
        long base = 0L;
        for (int i = 0; i < amounts.length; i++) {
            if (matched[i]) {
                base += amounts[i];
            }
        }
        long d = Math.min(Math.max(discount, 0L), base);
        if (d <= 0 || base <= 0) {
            return 0L;
        }
        if (d == base) {
            for (int i = 0; i < amounts.length; i++) {
                if (matched[i]) {
                    perLine[i] = amounts[i];
                    amounts[i] = 0L;
                }
            }
            return d;
        }
        long assigned = 0L;
        List<Integer> order = new ArrayList<>();
        long[] remainders = new long[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            if (!matched[i]) {
                continue;
            }
            long exact = d * amounts[i];
            long part = exact / base;
            remainders[i] = exact % base;
            perLine[i] = part;
            assigned += part;
            order.add(i);
        }
        long left = d - assigned;
        order.sort((a, b) -> {
            int cmp = Long.compare(remainders[b], remainders[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        for (int k = 0; k < order.size() && left > 0; k++) {
            int i = order.get(k);
            if (perLine[i] < amounts[i]) {
                perLine[i] += 1;
                left--;
            }
        }
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] -= perLine[i];
        }
        return d;
    }

    // ===== 输出组装 =====

    private static PromotionResult.HitResult hitOf(PromotionRule rule, long[] perLine, List<CartLine> lines,
                                                   long amount) {
        PromotionResult.HitResult hit = new PromotionResult.HitResult();
        hit.setPromotionId(rule.getId());
        hit.setPromotionName(rule.getName());
        hit.setType(rule.getType());
        hit.setTypeText(PromotionTypes.typeText(rule.getType()));
        hit.setRuleText(rule.ruleText());
        hit.setAmount(amount);
        for (int i = 0; i < perLine.length; i++) {
            if (perLine[i] <= 0) {
                continue;
            }
            CartLine line = lines.get(i);
            hit.getLineDetails().add(new PromotionResult.HitLine(
                    line == null ? null : line.getSkuId(),
                    line == null ? null : line.getGoodsId(),
                    line == null ? null : line.getMerchantId(),
                    perLine[i]));
        }
        return hit;
    }

    private static int phaseOf(PromotionRule rule) {
        return PromotionTypes.isRateType(rule.getType()) ? 0 : 1;
    }

    private static int ord(PromotionRule rule) {
        return rule.getPriority() == null ? 100 : rule.getPriority();
    }

    private static long idOf(PromotionRule rule) {
        return rule.getId() == null ? Long.MAX_VALUE : rule.getId();
    }
}
