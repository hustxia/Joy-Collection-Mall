package com.mall.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 热搜词聚合（V14 纯逻辑）：把「关键词 → 搜索次数」的原始统计规整成前端可直接用的 TopN。
 *
 * <p>口径：
 * <ol>
 *   <li>空词、单字符词不进热搜（"a"/"手" 没有运营价值，还容易占位）；</li>
 *   <li>同名词合并计数（大小写不敏感），保留最先出现的写法；</li>
 *   <li>次数相同按词长降序（更具体的词优先），再按字典序，保证结果稳定不抖动；</li>
 *   <li>统计为空时回落到词表权重（{@code mall_search_term.weight}），新站点也有热搜可展示。</li>
 * </ol>
 */
public final class HotKeywords {

    private HotKeywords() {
    }

    /** 原始统计行（关键词 + 次数） */
    public record Row(String keyword, long count) {
    }

    public static List<String> top(List<Row> rows, int limit) {
        List<String> out = new ArrayList<>();
        if (limit <= 0 || rows == null || rows.isEmpty()) {
            return out;
        }
        Map<String, long[]> merged = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (Row row : rows) {
            String kw = row.keyword() == null ? "" : row.keyword().trim();
            if (kw.length() < 2) {
                continue;
            }
            String key = kw.toLowerCase();
            display.putIfAbsent(key, kw);
            long[] cell = merged.computeIfAbsent(key, k -> new long[1]);
            cell[0] += Math.max(0L, row.count());
        }
        merged.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0)
                .sorted(Comparator
                        .<Map.Entry<String, long[]>>comparingLong(e -> e.getValue()[0]).reversed()
                        .thenComparing(e -> display.get(e.getKey()).length(), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .forEach(e -> out.add(display.get(e.getKey())));
        return out;
    }

    /** 统计为空时的兜底：按词表权重取前 limit 个词 */
    public static List<String> fallbackFromDictionary(List<TermDictionary.Term> terms, int limit) {
        List<String> out = new ArrayList<>();
        if (terms == null || limit <= 0) {
            return out;
        }
        terms.stream()
                .filter(t -> t.word() != null && !t.word().isBlank())
                .sorted(Comparator.comparingInt(TermDictionary.Term::weight).reversed())
                .limit(limit)
                .forEach(t -> out.add(t.word()));
        return out;
    }
}
