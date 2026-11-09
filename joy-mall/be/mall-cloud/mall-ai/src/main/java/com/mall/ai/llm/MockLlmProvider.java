package com.mall.ai.llm;

import org.springframework.stereotype.Component;

/**
 * 演示/测试提供方：不调用任何外部服务，直接返回编排器给的「事实草稿」。
 *
 * <p>它的存在保证三件事：CI 与离线演示不依赖网络；回答内容与工具取证 100% 一致；
 * 降级链最后一环永远是它，用户不会看到「服务不可用」白屏。
 */
@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        String draft = request.getGroundedDraft();
        if (draft == null || draft.isBlank()) {
            return LlmResult.fail(name(), "无草稿可返回");
        }
        // token 估算：中文按 1.5 字/token 粗算，仅用于成本看板量级参考
        int tokens = (int) Math.ceil(draft.length() / 1.5) + 32;
        return LlmResult.ok(draft, name(), tokens);
    }
}
