package com.mall.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索打分与排序（V14 纯逻辑，无 Spring / 无数据库）。
 *
 * <p>打分口径（分数只用于排序，不做归一化）：
 * <pre>
 *   标题精确等于某个检索词    +40（额外）
 *   标题包含某个检索词        +100
 *   副标题包含                +60
 *   品牌名包含                +30
 *   类目名包含                +20
 *   命中词来自同义词/拼音/纠错 +10（提示「猜你想搜」，同时保证原词命中优先）
 * </pre>
 * 同分时按销量降序、再按 id 降序——稳定排序，避免同分商品每次顺序抖动导致翻页重复。
 *
 * <p>这里不做「一词不命中就整体不返回」的严格 AND：检索词集合本身已经是扩召回结果
 * （同义词 + 二元片段），按命中数量加权更符合「越匹配越靠前」的直觉。
 */
public final class SearchRanker {

    static final int W_NAME = 100;
    static final int W_SUBTITLE = 60;
    static final int W_BRAND = 30;
    static final int W_CATEGORY = 20;
    static final int W_EXACT_NAME = 40;
    /** 检索词不是用户原词（同义词/拼音/纠错）时的补偿分：原词命中永远优先 */
    static final int W_REWRITE = 10;

    private SearchRanker() {
    }

    /** 全量打分（未命中任何字段的商品直接剔除），返回按得分降序的列表 */
    public static List<SearchHit> rank(List<SearchDoc> docs, SearchPlan plan) {
        List<SearchHit> hits = new ArrayList<>();
        if (docs == null || docs.isEmpty() || plan == null || plan.empty()) {
            return hits;
        }
        Set<String> primary = new HashSet<>();
        primary.add(plan.keyword());
        for (SearchDoc doc : docs) {
            int best = 0;
            String bestField = null;
            int matchedTerms = 0;
            for (String term : plan.terms()) {
                int s = scoreTerm(doc, term, primary.contains(term));
                if (s > 0) {
                    matchedTerms++;
                    if (s > best) {
                        best = s;
                        bestField = fieldOf(doc, term);
                    }
                }
            }
            for (String token : plan.tokens()) {
                int s = scoreTerm(doc, token, false) / 2;
                if (s > 0) {
                    matchedTerms++;
                    if (s > best) {
                        best = s;
                        bestField = fieldOf(doc, token);
                    }
                }
            }
            if (matchedTerms == 0) {
                continue;
            }
            // 多词命中加成：命中 3 个词相当于标题再命中一次，避免「副标题擦边」压过「标题全中」
            int score = best + Math.max(0, matchedTerms - 1) * 15;
            hits.add(new SearchHit(doc, score, bestField == null ? "标题" : bestField));
        }
        hits.sort(Comparator
                .comparingInt(SearchHit::score).reversed()
                .thenComparing(h -> h.doc().getSales() == null ? 0 : h.doc().getSales(), Comparator.reverseOrder())
                .thenComparing(h -> h.doc().getId() == null ? 0L : h.doc().getId(), Comparator.reverseOrder()));
        return hits;
    }

    private static int scoreTerm(SearchDoc doc, String term, boolean isPrimary) {
        if (term == null || term.isEmpty()) {
            return 0;
        }
        int bonus = isPrimary ? 0 : W_REWRITE;
        if (SearchTexts.containsIgnoreCase(doc.getName(), term)) {
            int s = W_NAME + bonus;
            if (doc.getName() != null && doc.getName().equalsIgnoreCase(term)) {
                s += W_EXACT_NAME;
            }
            return s;
        }
        if (SearchTexts.containsIgnoreCase(doc.getSubTitle(), term)) {
            return W_SUBTITLE + bonus;
        }
        if (SearchTexts.containsIgnoreCase(doc.getBrandName(), term)) {
            return W_BRAND + bonus;
        }
        if (SearchTexts.containsIgnoreCase(doc.getCategoryName(), term)) {
            return W_CATEGORY + bonus;
        }
        return 0;
    }

    private static String fieldOf(SearchDoc doc, String term) {
        if (SearchTexts.containsIgnoreCase(doc.getName(), term)) {
            return "标题";
        }
        if (SearchTexts.containsIgnoreCase(doc.getSubTitle(), term)) {
            return "副标题";
        }
        if (SearchTexts.containsIgnoreCase(doc.getBrandName(), term)) {
            return "品牌";
        }
        return "类目";
    }

    /**
     * 搜索联想（下拉 10 条）：词表命中优先 → 商品名切段命中 → 商品名兜底，去重后截断。
     *
     * <p>「切段」指把商品名按空格与分隔符拆开，只保留包含关键字的片段：
     * 「悦集 X7 Pro 旗舰手机」+「手机」→「旗舰手机」，比整串商品名更像一个搜索词。
     * 联想不查类目/品牌、不做纠错——下拉框必须毫秒级返回。
     */
    public static List<String> suggest(String keyword, List<SearchDoc> docs, List<String> dictionaryWords,
                                       int limit) {
        List<String> out = new ArrayList<>();
        if (limit <= 0) {
            return out;
        }
        String kw = SearchTexts.normalize(keyword);
        if (kw.isEmpty()) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        // 1) 词表：前缀命中 > 包含命中
        List<String> dictPrefix = new ArrayList<>();
        List<String> dictContains = new ArrayList<>();
        for (String word : dictionaryWords) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (SearchTexts.startsWithIgnoreCase(word, kw)) {
                dictPrefix.add(word);
            } else if (SearchTexts.containsIgnoreCase(word, kw)) {
                dictContains.add(word);
            }
        }
        dictPrefix.sort(Comparator.comparingInt(String::length));
        for (String w : dictPrefix) {
            addIf(seen, out, w, limit);
        }
        // 2) 商品名切段（只留含关键字的片段，过长片段截断成「关键字 + 后 4 字」）
        for (SearchDoc doc : docs) {
            for (String seg : segments(doc.getName())) {
                if (!SearchTexts.containsIgnoreCase(seg, kw)) {
                    continue;
                }
                addIf(seen, out, clip(seg, kw), limit);
            }
        }
        for (String w : dictContains) {
            addIf(seen, out, w, limit);
        }
        // 3) 兜底：商品全名（用户可能就想整串搜）
        for (SearchDoc doc : docs) {
            addIf(seen, out, doc.getName(), limit);
        }
        return out;
    }

    private static List<String> segments(String name) {
        List<String> segs = new ArrayList<>();
        if (name == null) {
            return segs;
        }
        for (String part : name.split("[\\s|/、,，()（）\\[\\]【】]+")) {
            if (!part.isBlank()) {
                segs.add(part.trim());
            }
        }
        return segs;
    }

    /** 片段太长时只保留关键字附近 8 个字，避免下拉框被一整行商品名撑开 */
    private static String clip(String seg, String kw) {
        if (seg.length() <= 10) {
            return seg;
        }
        int idx = seg.toLowerCase().indexOf(kw.toLowerCase());
        int start = Math.max(0, idx - 3);
        int end = Math.min(seg.length(), start + 10);
        return seg.substring(start, end);
    }

    private static void addIf(Set<String> seen, List<String> out, String word, int limit) {
        if (out.size() >= limit || word == null || word.isBlank()) {
            return;
        }
        String w = word.trim();
        if (seen.add(w.toLowerCase())) {
            out.add(w);
        }
    }
}
