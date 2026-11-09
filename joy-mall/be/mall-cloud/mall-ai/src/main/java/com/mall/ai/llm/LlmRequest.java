package com.mall.ai.llm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次生成请求。
 *
 * <p>{@code groundedDraft} 是编排器用检索/工具证据拼出的「事实草稿」：
 * LLM 的职责是把它说人话，而不是自由发挥。mock 实现直接返回草稿，
 * 因此无 Key、断网时全链路依然可演示，且答案与真实数据一致。
 */
@Data
public class LlmRequest {
    private String systemPrompt;
    private List<LlmMessage> messages = new ArrayList<>();
    private String groundedDraft;
    private int maxTokens = 600;
    private double temperature = 0.3;
}
