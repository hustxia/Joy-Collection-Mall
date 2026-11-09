package com.mall.ai.llm;

/** 生成结果：ok=false 表示该 provider 不可用（交由路由降级） */
public record LlmResult(boolean ok, String content, String provider, int tokens, String error) {

    public static LlmResult ok(String content, String provider, int tokens) {
        return new LlmResult(true, content, provider, tokens, null);
    }

    public static LlmResult fail(String provider, String error) {
        return new LlmResult(false, null, provider, 0, error);
    }
}
