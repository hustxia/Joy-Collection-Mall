package com.mall.ai.support;

import com.mall.ai.entity.Faq;

/** FAQ 命中：score 用于阈值判断与后台调优 */
public record FaqHit(Faq faq, double score) {
}
