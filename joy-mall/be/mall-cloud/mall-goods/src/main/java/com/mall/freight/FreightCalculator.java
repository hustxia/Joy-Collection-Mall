package com.mall.freight;

/**
 * 运费计算器（V12）：纯函数，无 Spring 依赖，便于对算术口径做单测。
 *
 * <p>口径固定为「首段 + 续段向上取整」：
 * <pre>
 *   units <= firstUnit            → firstFee
 *   units >  firstUnit            → firstFee + ceil((units - firstUnit) / extraUnit) × extraFee
 * </pre>
 * 满额包邮（freeThreshold &gt; 0 且商品金额已达门槛）直接返回 0，优先级高于一切。
 *
 * <p>单位由调用方保证：按件计费传「件数」，按重量计费传「克」——两者算法一致，
 * 只是量纲不同（首重 1kg = 1000、续重 0.5kg = 500）。
 */
public final class FreightCalculator {

    private FreightCalculator() {
    }

    /**
     * @param chargeType    1按件 2按重量 3满额包邮
     * @param firstUnit     首件/首重（件或克）
     * @param firstFee      首费（分）
     * @param extraUnit     续件/续重步长（件或克）
     * @param extraFee      续费（分）
     * @param freeThreshold 满额免邮阈值（分），0=不启用
     * @param units         本单计费量（件数或克数）
     * @param goodsAmount   商品金额（分）
     * @return 运费（分），恒 &gt;= 0
     */
    public static long calc(int chargeType, int firstUnit, long firstFee,
                            int extraUnit, long extraFee, long freeThreshold,
                            long units, long goodsAmount) {
        if (freeThreshold > 0 && goodsAmount >= freeThreshold) {
            return 0L;
        }
        // 无商品（异常入参）：不收费，避免出现「0 件商品 8 元运费」
        if (units <= 0) {
            return 0L;
        }
        // 满额包邮型模板：未达门槛按固定首费收，不做续段累加
        if (chargeType == 3) {
            return Math.max(firstFee, 0L);
        }
        long fee = Math.max(firstFee, 0L);
        if (units > firstUnit && extraUnit > 0) {
            long extraUnits = units - firstUnit;
            long times = (extraUnits + extraUnit - 1) / extraUnit;
            fee += times * Math.max(extraFee, 0L);
        }
        return fee;
    }

    /** 距包邮门槛差额（分）：无门槛或已达门槛返回 0 */
    public static long freeGap(long freeThreshold, long goodsAmount) {
        if (freeThreshold <= 0 || goodsAmount >= freeThreshold) {
            return 0L;
        }
        return freeThreshold - goodsAmount;
    }

    /** 分 → 「¥xx.xx」（提示文案用） */
    public static String yuan(long fen) {
        return "¥" + java.math.BigDecimal.valueOf(fen)
                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }
}
