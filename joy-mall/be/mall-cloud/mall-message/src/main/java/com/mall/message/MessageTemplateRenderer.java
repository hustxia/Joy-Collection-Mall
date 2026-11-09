package com.mall.message;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染（V14 纯逻辑）：把 {@code ${var}} 替换成变量值。
 *
 * <p>三条刻意的口径：
 * <ol>
 *   <li><b>未提供的变量保留占位符</b>，不替换成空串——短信里突然缺一段金额比看到
 *       {@code ${amount}} 更难排查，宁可暴露问题；</li>
 *   <li><b>值为 null 视为未提供</b>；</li>
 *   <li><b>金额变量</b>（key 以 amount 结尾、值为整数分）按「分 → 元」转换，
 *       避免模板里写 {@code ${amount}} 却发出 499900 这种数字。</li>
 * </ol>
 */
public final class MessageTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    private MessageTemplateRenderer() {
    }

    public static String render(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = vars.get(key);
            String replacement = value == null ? matcher.group(0) : display(key, value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 金额字段（amount/refundAmount）按分转元，其余原样输出 */
    private static String display(String key, Object value) {
        String lower = key.toLowerCase();
        boolean moneyKey = lower.endsWith("amount") || lower.endsWith("price") || "fee".equals(lower);
        if (moneyKey && value instanceof Number number) {
            long fen = number.longValue();
            return String.format("%.2f", fen / 100.0);
        }
        return String.valueOf(value);
    }
}
