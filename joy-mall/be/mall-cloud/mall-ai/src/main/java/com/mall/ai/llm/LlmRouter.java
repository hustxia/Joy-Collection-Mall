package com.mall.ai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 降级链路由：备选 → mock（永远可用）。
 *
 * <p>顺序由 {@code app.ai.provider} 决定：openai → ollama → mock；
 * 也支持显式列出多级 {@code openai,ollama}。任一级异常/超时/未配 Key 自动下沉，
 * 保证接入层永远返回一个可展示的回答。
 */
@Slf4j
@Component
public class LlmRouter {

    private final List<LlmProvider> providers;
    private final List<String> order;

    public LlmRouter(List<LlmProvider> providers, @Value("${app.ai.provider:mock}") String configured) {
        this.providers = providers;
        List<String> chain = new ArrayList<>();
        for (String piece : configured.split(",")) {
            if (!piece.isBlank()) {
                chain.add(piece.trim());
            }
        }
        // 未显式配置 mock 时补齐兜底位
        if (!chain.contains("mock")) {
            chain.add("mock");
        }
        this.order = chain;
    }

    /** 返回首个可用 provider 的结果；全部失败时返回 mock 的草稿结果 */
    public LlmResult complete(LlmRequest request) {
        for (String name : order) {
            LlmProvider provider = pick(name);
            if (provider == null || !provider.available()) {
                continue;
            }
            long start = System.currentTimeMillis();
            LlmResult result = provider.complete(request);
            if (result.ok()) {
                log.debug("llm provider={} cost={}ms tokens={}", name, System.currentTimeMillis() - start, result.tokens());
                return result;
            }
            log.info("llm provider {} 不可用（{}），降级下一级", name, result.error());
        }
        // 理论上到不了：mock 永远可用
        return LlmResult.ok(request.getGroundedDraft(), "faq", 0);
    }

    public String activeProvider() {
        return order.get(0);
    }

    private LlmProvider pick(String name) {
        return providers.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }
}
