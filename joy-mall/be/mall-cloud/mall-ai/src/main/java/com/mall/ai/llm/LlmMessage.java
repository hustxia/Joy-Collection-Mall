package com.mall.ai.llm;

/** 对话消息（role: system/user/assistant） */
public record LlmMessage(String role, String content) {
}
