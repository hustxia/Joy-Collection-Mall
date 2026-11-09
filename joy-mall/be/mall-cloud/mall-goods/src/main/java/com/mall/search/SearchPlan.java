package com.mall.search;

import java.util.List;

/**
 * 一次搜索的检索计划（V14）：把「用户输入」翻译成「一组可用于匹配的词」。
 *
 * @param raw         原始输入（未规范化，日志留痕用）
 * @param keyword     规范化后的词（trim + 小写 + 全角转半角 + 连续空白折叠）
 * @param terms       参与匹配的词集合：原词 → 同义词 → 拼音对应词 / 纠错词
 * @param tokens      二元切分片段（仅当输入是 ≥3 个连续汉字时才产生）
 * @param pinyinHit   是否由拼音（全拼/首字母）命中词表
 * @param correctedFrom 纠错前的词；未纠错为 null
 * @param categoryId  词表里配置的类目（无结果兜底用）；null 表示不限类目
 * @param empty       输入是否为空（空输入直接返回空结果，不落日志）
 */
public record SearchPlan(
        String raw,
        String keyword,
        List<String> terms,
        List<String> tokens,
        boolean pinyinHit,
        String correctedFrom,
        Long categoryId,
        boolean empty) {

    public static SearchPlan empty(String raw) {
        return new SearchPlan(raw, "", List.of(), List.of(), false, null, null, true);
    }

    /** 是否发生过「输入 → 其它词」的转换（拼音或纠错），前端可提示「已为您搜索 xx」 */
    public boolean rewritten() {
        return pinyinHit || correctedFrom != null;
    }
}
