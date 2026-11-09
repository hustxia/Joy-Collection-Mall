package com.mall.ai.support;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则意图分类：不训练模型、不调 LLM。
 *
 * <p>理由：意图只决定「去查什么」，判错的代价比多查一次高得多；
 * 用关键词 + 订单号正则既快又稳定，也避免了小模型分类漂移。
 */
@Component
public class IntentClassifier {

    private static final List<String> HUMAN_WORDS = List.of(
            "转人工", "人工客服", "找人工", "真人", "投诉", "举报", "太差了", "没人管");

    /** 订单号形态：12~32 位数字（含空格/横线先归一化） */
    private static final java.util.regex.Pattern ORDER_NO = java.util.regex.Pattern.compile("\\d{12,32}");

    public Intent classify(String text) {
        if (text == null || text.isBlank()) {
            return Intent.CHITCHAT;
        }
        String t = text.trim();
        if (containsAny(t, HUMAN_WORDS)) {
            return Intent.HUMAN;
        }
        // 售后词在前：否则「退款什么时候到」会被「什么时候到」抢成查物流
        if (containsAny(t, List.of("退款", "退货", "换货", "售后", "退货退款", "钱没退", "退回来", "退了没"))) {
            return Intent.REFUND;
        }
        if (ORDER_NO.matcher(normalize(t)).find() || containsAny(t, List.of(
                "我的订单", "订单状态", "订单号", "买的东西", "到哪了", "到哪儿了", "发货没", "什么时候到"))) {
            return Intent.ORDER;
        }
        if (containsAny(t, List.of("物流", "快递", "运单", "签收", "派送", "延误", "没收到"))) {
            return Intent.LOGISTICS;
        }
        if (containsAny(t, List.of("运费", "邮费", "包邮", "发票", "优惠券", "积分", "会员", "等级",
                "秒杀", "拼团", "抽奖", "分销", "佣金", "支付", "怎么用", "规则", "政策"))) {
            return Intent.FAQ;
        }
        return Intent.CHITCHAT;
    }

    /** 从自然语言里抠订单号（去掉空格与横线后再匹配） */
    public String extractOrderNo(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = ORDER_NO.matcher(normalize(text));
        return m.find() ? m.group() : null;
    }

    private String normalize(String text) {
        return text.replaceAll("[\\s\\-]", "");
    }

    private boolean containsAny(String text, List<String> words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
