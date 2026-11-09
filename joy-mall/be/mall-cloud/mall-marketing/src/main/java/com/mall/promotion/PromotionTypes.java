package com.mall.promotion;

/**
 * 促销活动类型与作用范围常量（V13，纯常量，不依赖 Spring）。
 *
 * <p>类型语义：
 * <ul>
 *   <li>{@link #FULL_CUT} 满减：门槛按「活动范围内商品原价小计」判定，减固定金额；</li>
 *   <li>{@link #FULL_DISCOUNT} 满折：同上门槛，按折扣率打折；</li>
 *   <li>{@link #LIMITED_DISCOUNT} 限时折扣：无门槛，按折扣率打折；</li>
 *   <li>{@link #SECOND_HALF} 第二件半价：范围内每满 2 件，第 2 件按折扣率计；</li>
 *   <li>{@link #BUNDLE} 套装：范围内每满 bundleQty 件按 bundlePrice 打包价计；</li>
 *   <li>{@link #ADD_ON} 加价购：满门槛后可按 addOnPrice 加购，本计算器只做命中标记（不改主商品价）。</li>
 * </ul>
 */
public final class PromotionTypes {

    private PromotionTypes() {
    }

    public static final int FULL_CUT = 1;
    public static final int FULL_DISCOUNT = 2;
    public static final int LIMITED_DISCOUNT = 3;
    public static final int SECOND_HALF = 4;
    public static final int BUNDLE = 5;
    public static final int ADD_ON = 6;

    public static final int SCOPE_ALL = 1;
    public static final int SCOPE_CATEGORY = 2;
    public static final int SCOPE_GOODS = 3;
    public static final int SCOPE_MERCHANT = 4;

    public static String typeText(Integer type) {
        if (type == null) {
            return "-";
        }
        return switch (type) {
            case FULL_CUT -> "满减";
            case FULL_DISCOUNT -> "满折";
            case LIMITED_DISCOUNT -> "限时折扣";
            case SECOND_HALF -> "第二件半价";
            case BUNDLE -> "套装";
            case ADD_ON -> "加价购";
            default -> String.valueOf(type);
        };
    }

    public static String scopeText(Integer scope) {
        if (scope == null) {
            return "-";
        }
        return switch (scope) {
            case SCOPE_ALL -> "全场";
            case SCOPE_CATEGORY -> "类目";
            case SCOPE_GOODS -> "商品";
            case SCOPE_MERCHANT -> "店铺";
            default -> String.valueOf(scope);
        };
    }

    /** 比例类活动（打折先行），定额类（满减）在后 */
    public static boolean isRateType(Integer type) {
        return type != null && (type == FULL_DISCOUNT || type == LIMITED_DISCOUNT
                || type == SECOND_HALF || type == BUNDLE);
    }
}
