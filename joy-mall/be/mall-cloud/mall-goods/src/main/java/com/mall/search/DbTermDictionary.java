package com.mall.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.entity.MallSearchTerm;
import com.mall.mapper.MallSearchTermMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 词表的数据库实现（V14）：全量载入内存 + 5 分钟过期刷新。
 *
 * <p>词表是运营配置类数据（百~千行量级），每次搜索都查库既浪费连接又把 P95 拖到 10ms+；
 * 这里做进程内缓存，命中失败（表不存在/库抖动）时返回空词表，搜索自动退化为 LIKE 单关键词，
 * 保证「词典挂了也不影响能搜到东西」。
 */
@Slf4j
@Component
public class DbTermDictionary implements TermDictionary {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final MallSearchTermMapper termMapper;

    private volatile List<Term> cache = List.of();
    private volatile long loadedAt = 0L;

    public DbTermDictionary(MallSearchTermMapper termMapper) {
        this.termMapper = termMapper;
    }

    @Override
    public Term byWord(String word) {
        if (!StringUtils.hasText(word)) {
            return null;
        }
        // 先精确命中词本身；再反查同义词（用户直接搜「移动电话」也要落回「手机」这一组）
        for (Term t : terms()) {
            if (t.word().equalsIgnoreCase(word)) {
                return t;
            }
        }
        for (Term t : terms()) {
            for (String synonym : t.synonyms()) {
                if (synonym.equalsIgnoreCase(word)) {
                    return t;
                }
            }
        }
        return null;
    }

    @Override
    public Term byPinyin(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String s = input.toLowerCase();
        // 全拼优先（shouji），再退首字母（sj）；两者都要求精确，避免 "shou" 误配到 "shouji"
        for (Term t : terms()) {
            if (t.pinyin() != null && t.pinyin().equalsIgnoreCase(s)) {
                return t;
            }
        }
        for (Term t : terms()) {
            if (t.initial() != null && t.initial().equalsIgnoreCase(s)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public Term nearest(String word, int maxDistance) {
        if (!StringUtils.hasText(word)) {
            return null;
        }
        Term best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Term t : terms()) {
            int d = SearchTexts.editDistance(word, t.word(), maxDistance);
            if (d <= maxDistance && d < bestDistance) {
                best = t;
                bestDistance = d;
            }
        }
        return best;
    }

    @Override
    public List<Term> startsWith(String prefix, int limit) {
        List<Term> out = new ArrayList<>();
        if (!StringUtils.hasText(prefix)) {
            return out;
        }
        for (Term t : terms()) {
            if (SearchTexts.startsWithIgnoreCase(t.word(), prefix)
                    || SearchTexts.startsWithIgnoreCase(t.pinyin(), prefix)
                    || SearchTexts.startsWithIgnoreCase(t.initial(), prefix)) {
                out.add(t);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public List<Term> all() {
        return terms();
    }

    /** 缓存读取：到点或首次访问时整表重载；异常降级为空表而不是抛错 */
    private List<Term> terms() {
        long now = System.currentTimeMillis();
        if (loadedAt > 0 && now - loadedAt < TTL_MS) {
            return cache;
        }
        synchronized (this) {
            if (loadedAt > 0 && System.currentTimeMillis() - loadedAt < TTL_MS) {
                return cache;
            }
            try {
                List<MallSearchTerm> rows = termMapper.selectList(
                        new LambdaQueryWrapper<MallSearchTerm>()
                                .eq(MallSearchTerm::getStatus, 1)
                                .orderByDesc(MallSearchTerm::getWeight));
                List<Term> loaded = new ArrayList<>(rows.size());
                for (MallSearchTerm r : rows) {
                    loaded.add(new Term(r.getWord(), split(r.getSynonyms()), r.getPinyin(),
                            r.getPinyinInitial(), r.getCategoryId(),
                            r.getWeight() == null ? 0 : r.getWeight()));
                }
                cache = loaded;
            } catch (Exception e) {
                log.warn("搜索词表加载失败，本次降级为单关键词 LIKE 检索：{}", e.getMessage());
                cache = List.of();
            }
            loadedAt = System.currentTimeMillis();
            return cache;
        }
    }

    private List<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }
}
