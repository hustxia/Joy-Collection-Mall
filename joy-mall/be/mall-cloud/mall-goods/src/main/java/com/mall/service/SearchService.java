package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.entity.MallBrand;
import com.mall.entity.MallCategory;
import com.mall.entity.MallGoods;
import com.mall.entity.MallSearchLog;
import com.mall.mapper.MallBrandMapper;
import com.mall.mapper.MallCategoryMapper;
import com.mall.mapper.MallGoodsMapper;
import com.mall.mapper.MallSearchLogMapper;
import com.mall.search.HotKeywords;
import com.mall.search.SearchDoc;
import com.mall.search.SearchHit;
import com.mall.search.SearchPlan;
import com.mall.search.SearchPlanner;
import com.mall.search.SearchRanker;
import com.mall.search.TermDictionary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 搜索服务（V14）：词表扩召回 → 候选集 → 打分排序 → 搜索日志 → 无结果兜底。
 *
 * <p><b>为什么不用 Elasticsearch：</b>本机离线、无 ES 集群。过渡方案是
 * 「MySQL 全文索引（ngram）+ 词表（同义词/拼音/首字母）+ LIKE 兜底」三件套：
 * <ul>
 *   <li>全文索引用于关键词检索的加速（见 {@code app.search.fulltext-enabled}），
 *       建索引脚本在 {@code db/v14-experience.sql}，守护式创建；</li>
 *   <li>词表解决中文分词与拼音/同义词（二元切分只在代码里做，见 {@link SearchPlanner}）；</li>
 *   <li>LIKE 兜底保证「词表没收录也不会 0 结果」——召回优先于精确。</li>
 * </ul>
 *
 * <p><b>性能口径：</b>候选集上限 {@value #CANDIDATE_LIMIT} 条，排序在内存完成；
 * 1000 商品量级下 P95 目标 < 200ms（单次 2~3 条 SQL：候选 + 品牌 + 类目）。
 */
@Slf4j
@Service
public class SearchService {

    /** 候选集上限：超过这个量的打分排序没有收益，只会拖慢响应 */
    private static final int CANDIDATE_LIMIT = 200;
    /** 单次检索最多展开的词数（同义词 + 二元片段），防止 SQL 参数爆炸 */
    private static final int MAX_TERMS = 12;
    /** 无结果兜底条数 */
    private static final int FALLBACK_LIMIT = 8;
    /** 联想条数（需求：下拉 10 条） */
    private static final int SUGGEST_LIMIT = 10;
    /** 热搜条数 */
    private static final int HOT_LIMIT = 10;

    private final MallGoodsMapper goodsMapper;
    private final MallBrandMapper brandMapper;
    private final MallCategoryMapper categoryMapper;
    private final MallSearchLogMapper searchLogMapper;
    private final TermDictionary dictionary;
    private final JdbcTemplate jdbcTemplate;
    private final SearchPlanner planner;

    /** 全文索引开关：默认关闭（不同 MySQL 发行版 ngram 解析器可用性不一致），开启后失败仍回落 LIKE */
    @Value("${app.search.fulltext-enabled:false}")
    private boolean fulltextEnabled;

    public SearchService(MallGoodsMapper goodsMapper,
                         MallBrandMapper brandMapper,
                         MallCategoryMapper categoryMapper,
                         MallSearchLogMapper searchLogMapper,
                         TermDictionary dictionary,
                         JdbcTemplate jdbcTemplate) {
        this.goodsMapper = goodsMapper;
        this.brandMapper = brandMapper;
        this.categoryMapper = categoryMapper;
        this.searchLogMapper = searchLogMapper;
        this.dictionary = dictionary;
        this.jdbcTemplate = jdbcTemplate;
        this.planner = new SearchPlanner(dictionary);
    }

    /**
     * 商品搜索。
     *
     * @param keyword  用户输入（可空：空输入返回空结果，不写日志）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数（上限 60，与 /shop/goods 口径一致）
     * @param memberId 会员ID（未登录 null，日志留痕用）
     */
    public Map<String, Object> search(String keyword, long pageNum, long pageSize, Long memberId) {
        SearchPlan plan = planner.plan(keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", plan.keyword());
        data.put("pinyinHit", plan.pinyinHit());
        data.put("correctedFrom", plan.correctedFrom());
        data.put("rewritten", plan.rewritten());
        if (plan.empty()) {
            data.put("total", 0);
            data.put("records", List.of());
            data.put("fallback", false);
            data.put("tip", "请输入搜索词");
            return data;
        }

        List<SearchHit> hits = SearchRanker.rank(loadCandidates(plan), plan);
        long total = hits.size();
        long size = Math.min(Math.max(pageSize, 1), 60);
        long page = Math.max(pageNum, 1);
        int from = (int) Math.min((page - 1) * size, hits.size());
        int to = (int) Math.min(from + size, hits.size());
        List<Map<String, Object>> records = hits.subList(from, to).stream()
                .map(this::toRow).toList();

        boolean fallback = hits.isEmpty();
        if (fallback) {
            records = fallbackRecords(plan);
        }
        data.put("total", total);
        data.put("records", records);
        data.put("fallback", fallback);
        data.put("tip", tipOf(plan, total));
        writeLog(plan, (int) total, memberId);
        return data;
    }

    /** 搜索联想：下拉 10 条，永不抛错（前端边输边调，报错会打断输入） */
    public List<String> suggest(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            return List.of();
        }
        List<String> dictWords = new ArrayList<>();
        for (TermDictionary.Term t : dictionary.startsWith(kw, SUGGEST_LIMIT)) {
            dictWords.add(t.word());
        }
        // 拼音输入：先翻译成中文词，再拿中文词去捞商品名
        TermDictionary.Term pinyinTerm = dictionary.byPinyin(kw);
        String matchWord = pinyinTerm == null ? kw : pinyinTerm.word();
        List<SearchDoc> docs = new ArrayList<>();
        try {
            List<MallGoods> goods = goodsMapper.selectList(new LambdaQueryWrapper<MallGoods>()
                    .eq(MallGoods::getStatus, 1)
                    .and(w -> w.like(MallGoods::getName, matchWord).or().like(MallGoods::getSubTitle, matchWord))
                    .orderByDesc(MallGoods::getSales)
                    .last("LIMIT 30"));
            for (MallGoods g : goods) {
                SearchDoc doc = new SearchDoc();
                doc.setId(g.getId());
                doc.setName(g.getName());
                doc.setSubTitle(g.getSubTitle());
                doc.setSales(g.getSales());
                docs.add(doc);
            }
        } catch (Exception e) {
            log.warn("搜索联想查询商品失败，仅返回词表候选：{}", e.getMessage());
        }
        return SearchRanker.suggest(matchWord, docs, dictWords, SUGGEST_LIMIT);
    }

    /** 热搜词：近 7 天有结果的搜索按次数排序；无统计数据时回落到词表权重 */
    public List<Map<String, Object>> hot() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<HotKeywords.Row> raw = new ArrayList<>();
        try {
            jdbcTemplate.query(
                    "SELECT keyword, COUNT(*) AS c FROM mall_search_log"
                            + " WHERE deleted = 0 AND hit = 1 AND keyword <> ''"
                            + " AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)"
                            + " GROUP BY keyword ORDER BY c DESC LIMIT 50",
                    rs -> {
                        raw.add(new HotKeywords.Row(rs.getString("keyword"), rs.getLong("c")));
                    });
        } catch (Exception e) {
            log.warn("热搜统计查询失败，回落词表权重：{}", e.getMessage());
        }
        List<String> words = HotKeywords.top(raw, HOT_LIMIT);
        if (words.isEmpty()) {
            words = HotKeywords.fallbackFromDictionary(dictionary.all(), HOT_LIMIT);
        }
        for (int i = 0; i < words.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword", words.get(i));
            row.put("rank", i + 1);
            rows.add(row);
        }
        return rows;
    }

    // ===== 私有辅助 =====

    /** 候选集：LIKE（或全文索引命中）后按销量取前 200 条，再交给纯逻辑打分 */
    private List<SearchDoc> loadCandidates(SearchPlan plan) {
        List<String> words = new ArrayList<>(plan.terms());
        words.addAll(plan.tokens());
        if (words.size() > MAX_TERMS) {
            words = new ArrayList<>(words.subList(0, MAX_TERMS));
        }
        List<MallGoods> goods = null;
        if (fulltextEnabled) {
            goods = candidatesByFulltext(plan, words);
        }
        if (goods == null) {
            goods = candidatesByLike(words);
        }
        if (goods.isEmpty()) {
            return List.of();
        }
        Map<Long, String> brandNames = brandNames(goods);
        Map<Long, String> categoryNames = categoryNames(goods);
        List<SearchDoc> docs = new ArrayList<>(goods.size());
        for (MallGoods g : goods) {
            SearchDoc doc = new SearchDoc();
            doc.setId(g.getId());
            doc.setName(g.getName());
            doc.setSubTitle(g.getSubTitle());
            doc.setBrandName(g.getBrandId() == null ? null : brandNames.get(g.getBrandId()));
            doc.setCategoryName(g.getCategoryId() == null ? null : categoryNames.get(g.getCategoryId()));
            doc.setCategoryId(g.getCategoryId());
            doc.setMainImage(g.getMainImage());
            doc.setPriceMin(g.getPriceMin());
            doc.setSales(g.getSales());
            docs.add(doc);
        }
        return docs;
    }

    /**
     * MySQL 全文索引路径（可选）：单关键词且开启开关时优先用 MATCH...AGAINST。
     *
     * <p>索引缺失、解析器不支持、语法报错等一律返回 null，由调用方回落 LIKE——
     * 全文索引是加速器，不是正确性依赖。
     */
    private List<MallGoods> candidatesByFulltext(SearchPlan plan, List<String> words) {
        if (words.size() != 1) {
            return null;
        }
        try {
            String kw = words.get(0).replaceAll("[+\\-<>()~*\"@]", " ").trim();
            if (kw.isEmpty()) {
                return null;
            }
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM mall_goods WHERE deleted = 0 AND status = 1"
                            + " AND MATCH(name, sub_title) AGAINST (? IN BOOLEAN MODE)"
                            + " ORDER BY sales DESC LIMIT " + CANDIDATE_LIMIT,
                    Long.class, kw);
            if (ids.isEmpty()) {
                return List.of();
            }
            return goodsMapper.selectBatchIds(ids);
        } catch (Exception e) {
            log.debug("全文索引检索不可用，回落 LIKE：{}", e.getMessage());
            return null;
        }
    }

    /** LIKE 兜底路径：标题/副标题命中，或品牌名、类目名命中的商品 */
    private List<MallGoods> candidatesByLike(List<String> words) {
        if (words.isEmpty()) {
            return List.of();
        }
        List<Long> brandIds = idsByName(words, true);
        List<Long> categoryIds = idsByName(words, false);
        LambdaQueryWrapper<MallGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallGoods::getStatus, 1);
        wrapper.and(w -> {
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (i > 0) {
                    w.or();
                }
                w.like(MallGoods::getName, word).or().like(MallGoods::getSubTitle, word);
            }
            if (!brandIds.isEmpty()) {
                w.or().in(MallGoods::getBrandId, brandIds);
            }
            if (!categoryIds.isEmpty()) {
                w.or().in(MallGoods::getCategoryId, categoryIds);
            }
        });
        wrapper.orderByDesc(MallGoods::getSales).orderByDesc(MallGoods::getId);
        wrapper.last("LIMIT " + CANDIDATE_LIMIT);
        return goodsMapper.selectList(wrapper);
    }

    /** 品牌 / 类目名的命中 id（名称是独立的运营配置，命中后要把整类商品带出来） */
    private List<Long> idsByName(List<String> words, boolean brand) {
        if (words.isEmpty()) {
            return List.of();
        }
        try {
            if (brand) {
                LambdaQueryWrapper<MallBrand> w = new LambdaQueryWrapper<>();
                w.and(q -> {
                    for (int i = 0; i < words.size(); i++) {
                        if (i > 0) {
                            q.or();
                        }
                        q.like(MallBrand::getName, words.get(i));
                    }
                });
                w.last("LIMIT 50");
                return brandMapper.selectList(w).stream().map(MallBrand::getId).toList();
            }
            LambdaQueryWrapper<MallCategory> w = new LambdaQueryWrapper<>();
            w.and(q -> {
                for (int i = 0; i < words.size(); i++) {
                    if (i > 0) {
                        q.or();
                    }
                    q.like(MallCategory::getName, words.get(i));
                }
            });
            w.last("LIMIT 50");
            return categoryMapper.selectList(w).stream().map(MallCategory::getId).toList();
        } catch (Exception e) {
            log.debug("品牌/类目名匹配失败（不影响标题检索）：{}", e.getMessage());
            return List.of();
        }
    }

    private Map<Long, String> brandNames(List<MallGoods> goods) {
        List<Long> ids = goods.stream().map(MallGoods::getBrandId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return brandMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(MallBrand::getId, b -> b.getName() == null ? "" : b.getName(),
                            (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<Long, String> categoryNames(List<MallGoods> goods) {
        List<Long> ids = goods.stream().map(MallGoods::getCategoryId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, MallCategory> map = categoryMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(MallCategory::getId, Function.identity(), (a, b) -> a));
            Map<Long, String> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(k, v.getName() == null ? "" : v.getName()));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 无结果兜底：优先同类目热销，类目不明时全站热销 */
    private List<Map<String, Object>> fallbackRecords(SearchPlan plan) {
        LambdaQueryWrapper<MallGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallGoods::getStatus, 1);
        if (plan.categoryId() != null) {
            wrapper.eq(MallGoods::getCategoryId, plan.categoryId());
        }
        wrapper.orderByDesc(MallGoods::getSales).orderByDesc(MallGoods::getId)
                .last("LIMIT " + FALLBACK_LIMIT);
        List<MallGoods> goods;
        try {
            goods = goodsMapper.selectList(wrapper);
        } catch (Exception e) {
            log.warn("无结果兜底查询失败：{}", e.getMessage());
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MallGoods g : goods) {
            Map<String, Object> row = baseRow(g);
            row.put("score", 0);
            row.put("matchedField", "同类热销");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toRow(SearchHit hit) {
        Map<String, Object> row = baseRow(hit.doc());
        row.put("score", hit.score());
        row.put("matchedField", hit.matchedField());
        return row;
    }

    private Map<String, Object> baseRow(MallGoods g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId() == null ? null : String.valueOf(g.getId()));
        row.put("name", g.getName());
        row.put("subTitle", g.getSubTitle());
        row.put("mainImage", g.getMainImage());
        row.put("priceMin", g.getPriceMin());
        row.put("sales", g.getSales());
        row.put("categoryId", g.getCategoryId() == null ? null : String.valueOf(g.getCategoryId()));
        return row;
    }

    private Map<String, Object> baseRow(SearchDoc doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId() == null ? null : String.valueOf(doc.getId()));
        row.put("name", doc.getName());
        row.put("subTitle", doc.getSubTitle());
        row.put("mainImage", doc.getMainImage());
        row.put("priceMin", doc.getPriceMin());
        row.put("sales", doc.getSales());
        row.put("categoryId", doc.getCategoryId() == null ? null : String.valueOf(doc.getCategoryId()));
        row.put("brandName", doc.getBrandName());
        row.put("categoryName", doc.getCategoryName());
        return row;
    }

    private String tipOf(SearchPlan plan, long total) {
        if (plan.pinyinHit()) {
            return "已按拼音「" + plan.keyword() + "」搜索到 " + total + " 件商品";
        }
        if (plan.correctedFrom() != null) {
            return "未找到「" + plan.correctedFrom() + "」，已为你搜索相近关键词";
        }
        return total == 0 ? "没有找到相关商品，看看同类热销" : "";
    }

    /** 搜索日志：写失败只打 WARN，绝不影响搜索结果（搜索是只读主链路） */
    private void writeLog(SearchPlan plan, int total, Long memberId) {
        try {
            MallSearchLog row = new MallSearchLog();
            row.setKeyword(plan.keyword());
            row.setMemberId(memberId);
            row.setResultCount(total);
            row.setHit(total > 0 ? 1 : 0);
            row.setPinyinHit(plan.pinyinHit() ? 1 : 0);
            row.setCorrectedFrom(plan.correctedFrom());
            searchLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("搜索日志写入失败（不影响搜索）：{}", e.getMessage());
        }
    }

    /** 供 /shop/goods?keyword= 复用的轻量入口保留：仅做词规范化，不做候选查询 */
    public String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? planner.plan(keyword).keyword() : "";
    }
}
