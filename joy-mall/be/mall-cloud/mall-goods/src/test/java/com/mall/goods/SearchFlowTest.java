package com.mall.goods;

import com.mall.search.HotKeywords;
import com.mall.search.SearchDoc;
import com.mall.search.SearchHit;
import com.mall.search.SearchPlan;
import com.mall.search.SearchPlanner;
import com.mall.search.SearchRanker;
import com.mall.search.SearchTexts;
import com.mall.search.TermDictionary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 搜索链路纯单测（V14，REQ-P1-04）。
 *
 * <p>刻意不启动 Spring、不连库：搜索的正确性 = 词表解析 + 打分排序，两段都是纯函数。
 * 这里用内存词表（与 db/v14-experience.sql 的种子同构）与内存商品集，锁住需求文档里的
 * 四条验收口径：搜「手机」命中、{@code shouji} 拼音命中、错字「手几」纠错命中、
 * 联想 10 条、热搜排序。真实 SQL 召回由服务级集成测试与手工冒烟覆盖。
 */
class SearchFlowTest {

    // ===== 夹具：与 db/v14-experience.sql 词表种子 / demo 商品同构 =====

    private static TermDictionary dictionary() {
        List<TermDictionary.Term> terms = new ArrayList<>();
        terms.add(new TermDictionary.Term("手机", List.of("移动电话", "智能手机"), "shouji", "sj", 201L, 100));
        terms.add(new TermDictionary.Term("笔记本", List.of("笔记本电脑", "轻薄本"), "bijiben", "bjb", 202L, 90));
        terms.add(new TermDictionary.Term("手表", List.of("智能手表"), "shoubiao", "sb", 203L, 80));
        terms.add(new TermDictionary.Term("冰箱", List.of(), "bingxiang", "bx", 204L, 70));
        terms.add(new TermDictionary.Term("双肩包", List.of("背包"), "shuangjianbao", "sjb", 207L, 50));
        return new InMemoryDictionary(terms);
    }

    private static List<SearchDoc> goods() {
        List<SearchDoc> docs = new ArrayList<>();
        docs.add(doc(1001L, "悦集 X7 Pro 旗舰手机", "骁龙旗舰芯片 | 2K 曲面屏 | 100W 快充", 201L, "悦集", 12580));
        docs.add(doc(1002L, "悦集 X7 标准版", "大电池长续航 | 5000mAh", 201L, "悦集", 8342));
        docs.add(doc(1003L, "云动 Book 14 轻薄本", "标压 R7 | 16G+1T | 1.29kg", 202L, "云动", 3210));
        docs.add(doc(1004L, "悦跑 Watch S2 智能手表", "血氧心率 | 14 天续航", 203L, "悦跑", 21055));
        docs.add(doc(1005L, "冰点 501L 风冷冰箱", "一级能效 | 双循环", 204L, "冰点", 1560));
        docs.add(doc(1008L, "木白 通勤双肩包", "16 寸电脑仓 | 防泼水面料", 207L, "木白", 9920));
        return docs;
    }

    private static SearchDoc doc(long id, String name, String subTitle, Long categoryId, String brand, int sales) {
        SearchDoc d = new SearchDoc();
        d.setId(id);
        d.setName(name);
        d.setSubTitle(subTitle);
        d.setCategoryId(categoryId);
        d.setBrandName(brand);
        d.setCategoryName(categoryName(categoryId));
        d.setSales(sales);
        return d;
    }

    private static String categoryName(Long categoryId) {
        return switch (categoryId.intValue()) {
            case 201 -> "手机数码";
            case 202 -> "电脑办公";
            case 203 -> "智能穿戴";
            case 204 -> "家用电器";
            default -> "箱包";
        };
    }

    // ===== 用例 =====

    @Test
    @DisplayName("搜「手机」：命中「悦集 X7 Pro 旗舰手机」，且标题命中排第一")
    void searchByChineseWord() {
        SearchPlanner planner = new SearchPlanner(dictionary());
        SearchPlan plan = planner.plan("手机");
        List<SearchHit> hits = SearchRanker.rank(goods(), plan);

        assertFalse(plan.empty());
        assertFalse(hits.isEmpty(), "搜「手机」必须至少命中一条");
        assertEquals(1001L, hits.get(0).doc().getId(), "标题含「手机」且销量最高，应排第一");
        assertTrue(hits.get(0).doc().getName().contains("手机"));
        assertEquals("标题", hits.get(0).matchedField());
    }

    @Test
    @DisplayName("拼音全拼 shouji 命中手机（pinyinHit=true，且不产生纠错）")
    void searchByPinyin() {
        SearchPlanner planner = new SearchPlanner(dictionary());
        SearchPlan plan = planner.plan("shouji");

        assertTrue(plan.pinyinHit(), "shouji 应识别为拼音输入");
        assertEquals("shouji", plan.correctedFrom() == null ? "shouji" : plan.correctedFrom());
        assertTrue(plan.terms().contains("手机"), "拼音需翻译成中文词再检索");
        assertTrue(plan.rewritten());

        List<SearchHit> hits = SearchRanker.rank(goods(), plan);
        assertFalse(hits.isEmpty());
        assertEquals(1001L, hits.get(0).doc().getId());
    }

    @Test
    @DisplayName("拼音首字母 sj 同样命中手机")
    void searchByPinyinInitial() {
        SearchPlan plan = new SearchPlanner(dictionary()).plan("sj");
        assertTrue(plan.pinyinHit());
        assertTrue(plan.terms().contains("手机"));
    }

    @Test
    @DisplayName("错字「手几」按编辑距离 1 纠错成「手机」并命中")
    void searchByTypo() {
        SearchPlanner planner = new SearchPlanner(dictionary());
        SearchPlan plan = planner.plan("手几");

        assertEquals("手几", plan.correctedFrom(), "纠错需留痕：原词写进 correctedFrom 供日志与前端提示");
        assertTrue(plan.terms().contains("手机"), "纠错后应带上正确词");
        assertTrue(plan.rewritten());

        List<SearchHit> hits = SearchRanker.rank(goods(), plan);
        assertFalse(hits.isEmpty(), "错字兜底不能 0 结果");
        assertEquals(1001L, hits.get(0).doc().getId());
    }

    @Test
    @DisplayName("同义词：搜「移动电话」反查词表后命中标题含「手机」的商品")
    void searchBySynonym() {
        SearchPlan plan = new SearchPlanner(dictionary()).plan("移动电话");

        assertFalse(plan.pinyinHit());
        assertTrue(plan.terms().contains("移动电话"));
        assertTrue(plan.terms().contains("手机"), "同义词需反查回主词，否则召回不到「手机」类商品");

        List<SearchHit> hits = SearchRanker.rank(goods(), plan);
        assertFalse(hits.isEmpty());
        assertEquals(1001L, hits.get(0).doc().getId());
    }

    @Test
    @DisplayName("联想：最多 10 条、全部包含关键字、无重复")
    void suggestAtMostTen() {
        List<String> dictWords = new ArrayList<>();
        for (TermDictionary.Term t : dictionary().all()) {
            dictWords.add(t.word());
        }
        List<String> suggestions = SearchRanker.suggest("手机", goods(), dictWords, 10);

        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.size() <= 10, "下拉最多 10 条");
        assertEquals(suggestions.size(), suggestions.stream().distinct().count(), "联想结果不能重复");
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("手机")));
    }

    @Test
    @DisplayName("联想：多商品时严格截断到 10 条，不会因候选多而超长")
    void suggestTruncated() {
        List<SearchDoc> many = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            SearchDoc d = new SearchDoc();
            d.setId((long) i);
            d.setName("悦集 旗舰手机 型号" + i);
            many.add(d);
        }
        List<String> suggestions = SearchRanker.suggest("手机", many, List.of(), 10);
        assertEquals(10, suggestions.size());
    }

    @Test
    @DisplayName("热搜：按次数降序，过滤空词与单字，次数相同按词长降序")
    void hotRanking() {
        List<HotKeywords.Row> rows = new ArrayList<>();
        rows.add(new HotKeywords.Row("手机", 9));
        rows.add(new HotKeywords.Row("手机", 3));      // 同名词合并 → 12
        rows.add(new HotKeywords.Row("手表", 5));
        rows.add(new HotKeywords.Row("双肩包", 5));    // 与手表同分，词更长 → 靠前
        rows.add(new HotKeywords.Row("a", 100));       // 单字符过滤
        rows.add(new HotKeywords.Row("  ", 50));       // 空白过滤
        rows.add(new HotKeywords.Row("冰箱", 0));      // 0 次过滤

        List<String> top = HotKeywords.top(rows, 10);
        assertEquals(List.of("手机", "双肩包", "手表"), top);
    }

    @Test
    @DisplayName("热搜兜底：无搜索日志时按词表权重取 TopN")
    void hotFallbackFromDictionary() {
        List<String> fallback = HotKeywords.fallbackFromDictionary(dictionary().all(), 3);
        assertEquals(List.of("手机", "笔记本", "手表"), fallback);
    }

    @Test
    @DisplayName("文本工具：二元切分 / 编辑距离 / 规范化")
    void textUtils() {
        assertEquals(List.of("智能", "能手", "手机"), SearchTexts.bigrams("智能手机"));
        assertEquals(1, SearchTexts.editDistance("手几", "手机", 1));
        // 超过上限直接剪枝返回 max+1：词表上千条时不至于每条都算完整个矩阵
        assertEquals(2, SearchTexts.editDistance("手表", "笔记本", 1));
        assertEquals("x7 pro 手机", SearchTexts.normalize("  X7   PRO   手机 "));
        assertEquals("x7", SearchTexts.normalize("Ｘ７"));
    }

    @Test
    @DisplayName("空输入：不查库、不命中、不打分")
    void emptyKeyword() {
        SearchPlan plan = new SearchPlanner(dictionary()).plan("   ");
        assertTrue(plan.empty());
        assertTrue(SearchRanker.rank(goods(), plan).isEmpty());
        assertTrue(SearchRanker.suggest("", goods(), List.of("手机"), 10).isEmpty());
    }

    @Test
    @DisplayName("完全不相关的词：0 命中（由 service 走同类热销兜底）")
    void noHit() {
        SearchPlan plan = new SearchPlanner(dictionary()).plan("轮胎");
        assertTrue(SearchRanker.rank(goods(), plan).isEmpty());
    }

    /** 内存词表：与生产 DbTermDictionary 同语义（精确 → 拼音 → 编辑距离 → 前缀） */
    private record InMemoryDictionary(List<TermDictionary.Term> terms) implements TermDictionary {

        @Override
        public Term byWord(String word) {
            return terms.stream()
                    .filter(t -> t.word().equalsIgnoreCase(word)
                            || t.synonyms().stream().anyMatch(s -> s.equalsIgnoreCase(word)))
                    .findFirst().orElse(null);
        }

        @Override
        public Term byPinyin(String input) {
            return terms.stream()
                    .filter(t -> (t.pinyin() != null && t.pinyin().equalsIgnoreCase(input))
                            || (t.initial() != null && t.initial().equalsIgnoreCase(input)))
                    .findFirst().orElse(null);
        }

        @Override
        public Term nearest(String word, int maxDistance) {
            Term best = null;
            int bestD = Integer.MAX_VALUE;
            for (Term t : terms) {
                int d = SearchTexts.editDistance(word, t.word(), maxDistance);
                if (d <= maxDistance && d < bestD) {
                    best = t;
                    bestD = d;
                }
            }
            return best;
        }

        @Override
        public List<Term> startsWith(String prefix, int limit) {
            return terms.stream()
                    .filter(t -> SearchTexts.startsWithIgnoreCase(t.word(), prefix)
                            || SearchTexts.startsWithIgnoreCase(t.pinyin(), prefix)
                            || SearchTexts.startsWithIgnoreCase(t.initial(), prefix))
                    .limit(limit).toList();
        }

        @Override
        public List<Term> all() {
            return terms;
        }
    }
}
