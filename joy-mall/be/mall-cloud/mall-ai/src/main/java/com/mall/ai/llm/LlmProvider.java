package com.mall.ai.llm;

/**
 * LLM 提供方 SPI —— 与 {@code PayChannel}、消息渠道同一套工程惯例：
 * 接口 + 配置化实现 + 降级链，调用方不感知具体厂商。
 */
public interface LlmProvider {

    /** 提供方标识：mock / openai / ollama */
    String name();

    /** 是否具备调用条件（Key 是否配置等） */
    boolean available();

    LlmResult complete(LlmRequest request);
}
