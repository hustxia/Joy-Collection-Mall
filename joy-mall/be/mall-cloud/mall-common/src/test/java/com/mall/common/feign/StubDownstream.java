package com.mall.common.feign;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试专用假下游：记录收到的请求，按需返回固定 JSON / 指定状态码 / 人为延迟。
 *
 * <p>用 JDK 自带 {@code HttpServer}，不引 WireMock/MockWebServer —— 契约与超时测试
 * 不该为一个桩再拉一套框架依赖，也保证离线能跑。
 */
final class StubDownstream implements AutoCloseable {

    record Recorded(String method, String path, String query, String body, String authorization) {
        String queryOrBody() {
            return (query == null ? "" : query) + "|" + (body == null ? "" : body);
        }
    }

    private final HttpServer server;
    private final List<Recorded> records = new CopyOnWriteArrayList<>();
    private volatile String responseBody = "{}";
    private volatile int responseStatus = 200;
    private volatile long delayMillis = 0L;

    StubDownstream() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 必须用线程池：默认 executor 串行处理，前一个用例的慢响应会把后一个用例一起拖超时
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        records.add(new Recorded(exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                body,
                exchange.getRequestHeaders().getFirst("Authorization")));
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (responseStatus == 503) {
            // Feign 只有在 503 + Retry-After 时才判为可重试异常（RetryableException）
            exchange.getResponseHeaders().add("Retry-After", "1");
        }
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        if (payload.length == 0) {
            exchange.sendResponseHeaders(responseStatus, -1);
        } else {
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(responseStatus, payload.length);
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(String json) {
        this.responseStatus = 200;
        this.responseBody = json;
    }

    void respondEmpty() {
        this.responseStatus = 200;
        this.responseBody = "";
    }

    void respondStatus(int status, String jsonBody) {
        this.responseStatus = status;
        this.responseBody = jsonBody;
    }

    void delay(long millis) {
        this.delayMillis = millis;
    }

    void reset() {
        records.clear();
        responseBody = "{}";
        responseStatus = 200;
        delayMillis = 0L;
    }

    Recorded last() {
        return records.isEmpty() ? null : records.get(records.size() - 1);
    }

    List<Recorded> all() {
        return new ArrayList<>(records);
    }

    int count() {
        return records.size();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
