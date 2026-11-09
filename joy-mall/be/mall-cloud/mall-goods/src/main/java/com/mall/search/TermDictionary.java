package com.mall.search;

import java.util.List;

/**
 * 搜索词表（V14）：{@code mall_search_term} 的领域抽象。
 *
 * <p>抽成接口的唯一目的：{@link SearchPlanner} 与 {@link SearchRanker} 能在纯单测里
 * 用内存实现跑通（不连库、不起 Spring），生产实现是 {@link DbTermDictionary}。
 */
public interface TermDictionary {

    /**
     * 词表条目。
     *
     * @param word      词本身
     * @param synonyms  同义词组（可空）
     * @param pinyin    全拼（可空）
     * @param initial   首字母（可空）
     * @param categoryId 关联类目（可空）
     * @param weight    权重（热搜兜底排序）
     */
    record Term(String word, List<String> synonyms, String pinyin, String initial,
                Long categoryId, int weight) {
    }

    /** 按词精确查（用于取同义词组） */
    Term byWord(String word);

    /** 按拼音查：全拼精确 / 首字母精确（用于 "shouji" → 手机） */
    Term byPinyin(String input);

    /** 编辑距离最小的词（≤ maxDistance 才算命中，用于错字纠错） */
    Term nearest(String word, int maxDistance);

    /** 前缀联想：词 / 全拼 / 首字母任一前缀命中 */
    List<Term> startsWith(String prefix, int limit);

    /** 全量词表（联想兜底与热搜兜底用，表规模小，调用方自行缓存） */
    List<Term> all();
}
