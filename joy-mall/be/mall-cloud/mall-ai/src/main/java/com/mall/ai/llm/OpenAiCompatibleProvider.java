package com.mall.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容协议（DeepSeek / 通义 / Kimi / vLLM 等改 base-url + model 即可）。
 *
 * <p>用 JDK HttpClient 而非引入 WebFlux：整个仓库是 MVC 栈，为一个非流式调用
 * 拉一套响应式依赖不划算；超时由 {@code timeout-ms} 控制，超时即降级。
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements LlmProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.ai.openai.base-url:}")
    private String baseUrl;

    @Value("${app.ai.openai.path:/chat/completions}")
    private String path;

    @Value("${app.ai.openai.api-key:}")
    private String apiKey;

    @Value("${app.ai.openai.model:deepseek-chat}")
    private String model;

    @Value("${app.ai.openai.timeout-ms:12000}")
    private long timeoutMs;

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        if (!available()) {
            return LlmResult.fail(name(), "未配置 API Key");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());
            ArrayNode messages = body.putArray("messages");
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
            }
            List<LlmMessage> list = request.getMessages();
            for (LlmMessage m : list) {
                messages.addObject().put("role", m.role()).put("content", m.content());
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return LlmResult.fail(name(), "HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int tokens = root.path("usage").path("total_tokens").asInt(0);
            if (content.isBlank()) {
                return LlmResult.fail(name(), "空回答");
            }
            return LlmResult.ok(content.trim(), name(), tokens);
        } catch (Exception e) {
            log.warn("openai provider failed: {}", e.getMessage());
            return LlmResult.fail(name(), e.getClass().getSimpleName());
        }
    }
}
