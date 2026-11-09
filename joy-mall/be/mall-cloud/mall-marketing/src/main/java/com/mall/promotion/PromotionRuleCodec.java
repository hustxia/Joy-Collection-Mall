package com.mall.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 活动规则 JSON 与计算模型之间的转换（V13）。
 *
 * <p>只认六个数值键，缺省一律回落 0/100 —— 运营在后台表单里没填的字段不应该让活动「算不出来」。
 * 未知键原样保留（读→改→写不丢配置），方便后续加字段而不改表。
 */
public final class PromotionRuleCodec {

    private static final ObjectMapper OM = new ObjectMapper();

    private PromotionRuleCodec() {
    }

    /** 解析规则 JSON；空串/非法 JSON 返回空对象节点（后续 get 全为缺省值） */
    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return OM.createObjectNode();
        }
        try {
            JsonNode node = OM.readTree(json);
            return node instanceof ObjectNode ? node : OM.createObjectNode();
        } catch (Exception e) {
            return OM.createObjectNode();
        }
    }

    /** 把规则 JSON 的数值字段灌进计算模型（id/名称/时间窗/优先级由调用方另外赋值） */
    public static void apply(PromotionRule rule, String json) {
        JsonNode node = parse(json);
        rule.setThreshold(longOf(node, "threshold", 0L));
        rule.setDiscount(longOf(node, "discount", 0L));
        rule.setDiscountRate(intOf(node, "discountRate", 100));
        rule.setBundleQty(intOf(node, "bundleQty", 0));
        rule.setBundlePrice(longOf(node, "bundlePrice", 0L));
        rule.setAddOnPrice(longOf(node, "addOnPrice", 0L));
    }

    /** 由计算模型反向生成规则 JSON（后台列表回显、种子数据生成用） */
    public static String toJson(PromotionRule rule) {
        ObjectNode node = OM.createObjectNode();
        node.put("threshold", rule.getThreshold() == null ? 0L : rule.getThreshold());
        node.put("discount", rule.getDiscount() == null ? 0L : rule.getDiscount());
        node.put("discountRate", rule.getDiscountRate() == null ? 100 : rule.getDiscountRate());
        node.put("bundleQty", rule.getBundleQty() == null ? 0 : rule.getBundleQty());
        node.put("bundlePrice", rule.getBundlePrice() == null ? 0L : rule.getBundlePrice());
        node.put("addOnPrice", rule.getAddOnPrice() == null ? 0L : rule.getAddOnPrice());
        return node.toString();
    }

    private static long longOf(JsonNode node, String key, long def) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? def : Math.max(v.asLong(def), 0L);
    }

    private static int intOf(JsonNode node, String key, int def) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? def : v.asInt(def);
    }
}
