package com.mall.search;

/**
 * 一条搜索结果：商品 + 得分 + 命中字段（前端据此显示「标题命中/品牌命中」）。
 */
public record SearchHit(SearchDoc doc, int score, String matchedField) {
}
