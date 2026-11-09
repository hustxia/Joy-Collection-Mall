package com.mall.promotion;

/**
 * 促销叠加策略（V13）。
 *
 * <p>由配置项 {@code mall.promotion.stack-mode} 决定，缺省 {@link #BEST}：
 * <ul>
 *   <li>{@link #BEST}：互斥取最优——所有活动各算一遍，只采纳优惠最大的那一个（并列取优先级高、ID 小）；</li>
 *   <li>{@link #STACK}：叠加——可叠加活动（stackable=1）按优先级依次作用于余额，
 *       不可叠加的只在「叠加后余额」上再择优采纳 1 个。</li>
 * </ul>
 *
 * <p>叠加模式下「比例类（满折/限时折扣/第二件半价/套装）先算、定额类（满减）后算」，
 * 门槛一律按<i>活动范围内商品原价小计</i>判定——这样「满300减50 + 9折」在叠加模式下
 * 稳定得到 {@code 300×0.9−50=220}，而不是「先减后折」的 225。
 */
public enum StackMode {

    /** 互斥取最优（默认） */
    BEST,
    /** 可叠加活动依次生效 */
    STACK;

    public static StackMode of(String raw) {
        if (raw == null || raw.isBlank()) {
            return BEST;
        }
        return "stack".equalsIgnoreCase(raw.trim()) ? STACK : BEST;
    }

    public String code() {
        return this == STACK ? "stack" : "best";
    }
}
