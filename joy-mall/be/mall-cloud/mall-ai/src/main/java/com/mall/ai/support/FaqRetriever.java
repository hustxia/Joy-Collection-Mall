package com.mall.ai.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.ai.entity.Faq;
import com.mall.ai.mapper.FaqMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * FAQ 检索：关键词 + 同义问法 + 二元组重合度，不引向量库。
 *
 * <p>规模判断：FAQ 是人工维护的数百条量级，全量加载进内存（60s 缓存）后逐条打分，
 * 单次耗时 &lt; 1ms，比维护一份向量索引简单得多；数据长到万级再换 ES（表里已留扩展位）。
 */
@Slf4j
@Component
public class FaqRetriever {

    private static final double HIT_THRESHOLD = 0.34;

    private final FaqMapper faqMapper;

    private volatile List<Faq> cache = List.of();
    private volatile long cacheAt = 0;

    public FaqRetriever(FaqMapper faqMapper) {
        this.faqMapper = faqMapper;
    }

    /** 命中返回最高分条目，未达阈值返回 null（宁可不答，也不乱答） */
    public FaqHit search(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String query = normalize(text);
        FaqHit best = null;
        for (Faq faq : candidates()) {
            double score = score(query, faq);
            if (best == null || score > best.score()) {
                best = new FaqHit(faq, score);
            }
        }
        if (best == null || best.score() < HIT_THRESHOLD) {
            return null;
        }
        try {
            faqMapper.increaseHit(best.faq().getId());
        } catch (Exception e) {
            log.debug("faq hit count failed: {}", e.getMessage());
        }
        return best;
    }

    /** 推荐追问：按分类取前 N 条启用中的标准问题 */
    public List<String> suggestions(String category, int limit) {
        List<String> list = new ArrayList<>();
        for (Faq faq : candidates()) {
            if (category != null && !category.equals(faq.getCategory())) {
                continue;
            }
            list.add(faq.getQuestion());
            if (list.size() >= limit) {
                break;
            }
        }
        return list;
    }

    public List<String> categories() {
        return candidates().stream().map(Faq::getCategory).distinct().toList();
    }

    public void invalidate() {
        cacheAt = 0;
    }

    private List<Faq> candidates() {
        long now = System.currentTimeMillis();
        if (now - cacheAt < 60_000 && !cache.isEmpty()) {
            return cache;
        }
        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Faq::getStatus, 1).orderByAsc(Faq::getId);
        cache = faqMapper.selectList(wrapper);
        cacheAt = now;
        return cache;
    }

    /**
     * 打分：同义问法整句包含记 1.0；否则按二元组重合度。
     * 用二元组而非分词，是因为中文短问句分词器在小词典下反而更不稳。
     */
    private double score(String query, Faq faq) {
        if (faq.getQuestion() != null && query.contains(normalize(faq.getQuestion()))) {
            return 1.0;
        }
        if (faq.getSynonyms() != null) {
            for (String syn : faq.getSynonyms().split(",")) {
                String s = normalize(syn);
                if (s.length() >= 2 && query.contains(s)) {
                    return 0.95;
                }
            }
        }
        double best = overlap(query, normalize(faq.getQuestion()));
        if (faq.getSynonyms() != null) {
            for (String syn : faq.getSynonyms().split(",")) {
                best = Math.max(best, overlap(query, normalize(syn)));
            }
        }
        return best;
    }

    private double overlap(String a, String b) {
        Set<String> ga = bigrams(a);
        Set<String> gb = bigrams(b);
        if (ga.isEmpty() || gb.isEmpty()) {
            return 0;
        }
        long inter = ga.stream().filter(gb::contains).count();
        return (double) inter / Math.min(ga.size(), gb.size());
    }

    private Set<String> bigrams(String s) {
        Set<String> set = new java.util.LinkedHashSet<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s，。！？、,.!?：:；;“”\"'（）()]", "");
    }

    /** 供后台预览：返回全部启用条目（已按命中数倒序） */
    public List<Faq> top(int limit) {
        return candidates().stream()
                .sorted(Comparator.comparing(Faq::getHitCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }
}
