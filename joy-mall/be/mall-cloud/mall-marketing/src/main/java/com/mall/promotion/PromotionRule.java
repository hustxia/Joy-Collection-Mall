package com.mall.promotion;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 促销活动规则（V13 计算器输入模型）。
 *
 * <p>刻意与数据库实体 {@code com.mall.entity.MallPromotion} 解耦：计算器只需要
 * 「一段时间窗 + 一个作用范围 + 一组阈值/折扣参数」，这样纯单测可以不碰 Spring、不碰库。
 */
@Data
public class PromotionRule {

    /** 活动ID（未落库的临时活动可给负数或 null，命中明细只做展示） */
    private Long id;

    /** 活动名称 */
    private String name;

    /** 类型：见 {@link PromotionTypes} */
    private Integer type;

    /** 作用范围：1全场2类目3商品4店铺 */
    private Integer scopeType = PromotionTypes.SCOPE_ALL;

    /** 门槛（分）：满减/满折/加价购用；0 表示无门槛 */
    private Long threshold = 0L;

    /** 立减金额（分）：满减用 */
    private Long discount = 0L;

    /** 折扣率：100=不打折，90=9折，50=半价（满折/限时折扣/第二件半价用） */
    private Integer discountRate = 100;

    /** 套装的件数（套装用） */
    private Integer bundleQty = 0;

    /** 套装的打包价（分，套装用） */
    private Long bundlePrice = 0L;

    /** 加价购的换购价（分，加价购用，仅展示） */
    private Long addOnPrice = 0L;

    /** 优先级：数值越小越优先 */
    private Integer priority = 100;

    /** 是否可与其他活动叠加 */
    private Boolean stackable = Boolean.FALSE;

    /** 开始时间（null=不限） */
    private LocalDateTime startTime;

    /** 结束时间（null=不限） */
    private LocalDateTime endTime;

    /** 作用商品ID集合（scopeType=3） */
    private Set<Long> goodsIds = new LinkedHashSet<>();

    /** 作用类目ID集合（scopeType=2） */
    private Set<Long> categoryIds = new LinkedHashSet<>();

    /** 作用店铺ID集合（scopeType=4） */
    private Set<Long> merchantIds = new LinkedHashSet<>();

    /** 时间窗判定：闭区间，null 侧不限 */
    public boolean activeAt(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        if (startTime != null && now.isBefore(startTime)) {
            return false;
        }
        return endTime == null || !now.isAfter(endTime);
    }

    /** 范围命中：范围集合为空时按「全场」处理（配置缺失不至于让整场活动失效） */
    public boolean matches(CartLine line) {
        if (line == null) {
            return false;
        }
        int scope = scopeType == null ? PromotionTypes.SCOPE_ALL : scopeType;
        return switch (scope) {
            case PromotionTypes.SCOPE_CATEGORY -> categoryIds.isEmpty()
                    || (line.getCategoryId() != null && categoryIds.contains(line.getCategoryId()));
            case PromotionTypes.SCOPE_GOODS -> goodsIds.isEmpty()
                    || (line.getGoodsId() != null && goodsIds.contains(line.getGoodsId()));
            case PromotionTypes.SCOPE_MERCHANT -> merchantIds.isEmpty()
                    || (line.getMerchantId() != null && merchantIds.contains(line.getMerchantId()));
            default -> true;
        };
    }

    /** 规则文案：给商品卡/详情页与后台列表复用 */
    public String ruleText() {
        int type = this.type == null ? 0 : this.type;
        String thresholdText = threshold == null || threshold <= 0 ? "" : "满" + yuan(threshold);
        return switch (type) {
            case PromotionTypes.FULL_CUT -> thresholdText + "减" + yuan(discount);
            case PromotionTypes.FULL_DISCOUNT -> thresholdText + "享" + rateText(discountRate);
            case PromotionTypes.LIMITED_DISCOUNT -> "限时" + rateText(discountRate);
            case PromotionTypes.SECOND_HALF -> "第二件" + rateText(discountRate);
            case PromotionTypes.BUNDLE -> "满" + (bundleQty == null ? 0 : bundleQty) + "件"
                    + yuan(bundlePrice) + "元";
            case PromotionTypes.ADD_ON -> thresholdText + "可加价" + yuan(addOnPrice) + "元换购";
            default -> name == null ? "" : name;
        };
    }

    private static String rateText(Integer rate) {
        int r = rate == null ? 100 : rate;
        if (r % 10 == 0) {
            return (r / 10) + "折";
        }
        return (r / 10.0) + "折";
    }

    private static String yuan(Long fen) {
        long v = fen == null ? 0L : fen;
        return (v % 100 == 0) ? String.valueOf(v / 100) : String.format("%.2f", v / 100.0);
    }
}
