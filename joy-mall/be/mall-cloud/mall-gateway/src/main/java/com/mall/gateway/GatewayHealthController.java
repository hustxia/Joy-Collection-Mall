package com.mall.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关健康聚合（V6 S-04）。
 *
 * <p>{@code GET /health} 仍透传给 mall-system（保持后台首页连通检测的既有契约）；
 * 聚合视图放在 {@code GET /health/aggregate}，并发探活 6 个业务服务 + 网关自身 + 数据库，
 * 任一项 DOWN 时整体 DEGRADED，但 HTTP 仍 200（不因下游故障整体 500）。
 */
@RestController
@RequestMapping("/health")
@Slf4j
public class GatewayHealthController {

    /** name=url,name=url；未配置时用内置默认（与 11 条路由端口一致） */
    @Value("${gateway.health.targets:}")
    private String targetsRaw;

    @Value("${gateway.health.timeout-ms:2000}")
    private long timeoutMs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final WebClient webClient = WebClient.builder().build();

    @GetMapping("/aggregate")
    public Mono<Map<String, Object>> aggregate() {
        Mono<Map<String, Object>> dbCheck = Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Map<String, Object> db = new LinkedHashMap<>();
            db.put("status", "UP");
            db.put("latencyMs", System.currentTimeMillis() - start);
            return db;
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> Mono.just(Map.of("status", "DOWN", "error", ex.getMessage())));

        return Flux.fromIterable(targets())
                .flatMap(this::probe)
                .collectList()
                .flatMap(services -> dbCheck.map(db -> assemble(services, db)));
    }

    private Map<String, Object> assemble(List<Map<String, Object>> services, Map<String, Object> db) {
        boolean allUp = "UP".equals(db.get("status"))
                && services.stream().allMatch(s -> "UP".equals(s.get("status")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", allUp ? "UP" : "DEGRADED");
        data.put("checkedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("services", services);
        data.put("db", db);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("msg", allUp ? "ok" : "部分服务不可用");
        body.put("data", data);
        return body;
    }

    private Mono<Map<String, Object>> probe(Target target) {
        // 自身不需要真发 HTTP（url 不是绝对地址，WebClient 会直接报错）
        if ("self".equals(target.url())) {
            return Mono.just(row(target, "UP", null));
        }
        return webClient.get()
                .uri(target.url() + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(body -> {
                    Object status = body == null ? null : body.get("status");
                    return row(target, status == null ? "DOWN" : String.valueOf(status), null);
                })
                .onErrorResume(ex -> Mono.just(row(target, "DOWN", ex.getClass().getSimpleName())));
    }

    private Map<String, Object> row(Target target, String status, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", target.name());
        row.put("url", target.url());
        row.put("status", status);
        if (error != null) {
            row.put("error", error);
        }
        return row;
    }

    private List<Target> targets() {
        List<Target> list = new ArrayList<>();
        // 网关自身：能执行到本方法即视为存活
        list.add(new Target("mall-gateway", "self"));
        if (targetsRaw == null || targetsRaw.isBlank()) {
            list.add(new Target("mall-system", "http://127.0.0.1:8201"));
            list.add(new Target("mall-goods", "http://127.0.0.1:8202"));
            list.add(new Target("mall-member", "http://127.0.0.1:8203"));
            list.add(new Target("mall-order", "http://127.0.0.1:8204"));
            list.add(new Target("mall-marketing", "http://127.0.0.1:8205"));
            list.add(new Target("mall-refund", "http://127.0.0.1:8206"));
            // V10：商家域
            list.add(new Target("mall-merchant", "http://127.0.0.1:8207"));
            return list;
        }
        for (String piece : targetsRaw.split(",")) {
            String trimmed = piece.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int idx = trimmed.indexOf('=');
            if (idx > 0) {
                list.add(new Target(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim()));
            }
        }
        return list;
    }

    private record Target(String name, String url) {
    }
}
