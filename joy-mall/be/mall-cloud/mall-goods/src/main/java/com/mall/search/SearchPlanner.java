package com.mall.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索计划生成器（V14 纯逻辑，无 Spring / 无数据库）。
 *
 * <p>输入用户敲的词，输出 {@link SearchPlan}：一次搜索要匹配哪些词。
 * 转换顺序固定为「词表直查 → 拼音 → 纠错 → 二元切分」：
 * <ol>
 *   <li><b>词表直查</b>：命中就拿同义词组（手机 ↔ 移动电话 / 智能手机）；</li>
 *   <li><b>拼音</b>：纯字母数字输入时按全拼/首字母查表（shouji / sj → 手机）；</li>
 *   <li><b>纠错</b>：词表里没有时找编辑距离 ≤1 的词（手几 → 手机），只纠一次；</li>
 *   <li><b>二元切分</b>：把「智能手机」切出「智能/能手/手机」，补召回。</li>
 * </ol>
 * 任何一步都不阻断后续：即使词表整张是空，也会带着原词走 LIKE，不会 0 召回。
 */
public final class SearchPlanner {

    /** 纠错容忍的最大编辑距离：>1 时误纠率明显上升（"手表"→"手机" 这类跨品类替换） */
    public static final int MAX_CORRECT_DISTANCE = 1;

    private final TermDictionary dictionary;

    public SearchPlanner(TermDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public SearchPlan plan(String rawKeyword) {
        String keyword = SearchTexts.normalize(rawKeyword);
        if (keyword.isEmpty()) {
            return SearchPlan.empty(rawKeyword);
        }
        Set<String> terms = new LinkedHashSet<>();
        terms.add(keyword);
        boolean pinyinHit = false;
        String correctedFrom = null;
        String correctedWord = null;
        Long categoryId = null;

        TermDictionary.Term byWord = dictionary.byWord(keyword);
        if (byWord != null) {
            // 命中的可能是同义词（用户搜「移动电话」），主词必须一起带上，否则召回不到「手机」类商品
            terms.add(byWord.word());
            terms.addAll(byWord.synonyms());
            categoryId = byWord.categoryId();
        } else if (SearchTexts.isAsciiAlnum(keyword)) {
            // 拼音路径：只对纯字母数字输入尝试，避免中文词被当成拼音去查首字母
            TermDictionary.Term byPinyin = dictionary.byPinyin(keyword);
            if (byPinyin != null) {
                pinyinHit = true;
                terms.add(byPinyin.word());
                terms.addAll(byPinyin.synonyms());
                categoryId = byPinyin.categoryId();
            }
        }
        if (!pinyinHit && byWord == null) {
            // 编辑距离 ≤1 的候选可能不止一个（"手几" 距 "手机"/"手表" 都是 1），
            // 词表按 weight 降序载入，这里取第一个命中即「权重最高」的词，结果稳定可解释。
            TermDictionary.Term nearest = dictionary.nearest(keyword, MAX_CORRECT_DISTANCE);
            if (nearest != null && !nearest.word().equals(keyword)) {
                correctedFrom = keyword;
                correctedWord = nearest.word();
                terms.add(nearest.word());
                terms.addAll(nearest.synonyms());
                categoryId = nearest.categoryId();
            }
        }

        // 二元切分只对「本词/纠错词」做，不对同义词做——同义词已由词表保证语义等价
        String base = correctedWord != null ? correctedWord : keyword;
        List<String> tokens = new ArrayList<>(SearchTexts.bigrams(base));
        tokens.removeIf(terms::contains);

        return new SearchPlan(rawKeyword, keyword, new ArrayList<>(terms), tokens,
                pinyinHit, correctedFrom, categoryId, false);
    }
}
