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

/**
 * 本地 Ollama（降级位）：机器只有 CPU 时 7B 模型首字慢，超时设 20s；
 * 不作为主力，只保证「没有外网/没有 Key」的场合仍能走真实模型。
 */
@Slf4j
@Component
public class OllamaLlmProvider implements LlmProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.ai.ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${app.ai.ollama.model:qwen2.5-coder:7b}")
    private String model;

    @Value("${app.ai.ollama.timeout-ms:20000}")
    private long timeoutMs;

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public boolean available() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            ObjectNode options = body.putObject("options");
            options.put("temperature", request.getTemperature());
            options.put("num_predict", request.getMaxTokens());
            ArrayNode messages = body.putArray("messages");
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
            }
            request.getMessages().forEach(m -> messages.addObject().put("role", m.role()).put("content", m.content()));

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(3000)).build();
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return LlmResult.fail(name(), "HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            String content = root.path("message").path("content").asText("");
            int tokens = root.path("eval_count").asInt(0) + root.path("prompt_eval_count").asInt(0);
            if (content.isBlank()) {
                return LlmResult.fail(name(), "空回答");
            }
            return LlmResult.ok(content.trim(), name(), tokens);
        } catch (Exception e) {
            log.warn("ollama provider failed: {}", e.getMessage());
            return LlmResult.fail(name(), e.getClass().getSimpleName());
        }
    }
}
