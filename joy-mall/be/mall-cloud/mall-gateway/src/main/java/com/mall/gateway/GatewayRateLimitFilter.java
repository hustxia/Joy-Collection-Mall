package com.mall.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关限流（V6 S-03）：对 C 端 {@code /shop/**} 按 IP 令牌桶限流，超限 429。
 *
 * <p>当前用内存令牌桶（单实例；本机无 Redis）。多实例部署时应替换为
 * Spring Cloud Gateway 的 {@code RequestRateLimiter} + Redis（{@code spring-boot-starter-data-redis-reactive}），
 * 本类的 {@link #tryAcquire} 是唯一替换点。
 */
@Component
@Slf4j
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private static final long IDLE_EVICT_MILLIS = 600_000L;
    private static final int MAX_KEYS = 10_000;

    @Value("${gateway.rate-limit.enabled:true}")
    private boolean enabled;

    /** 每秒补充令牌数（每 IP 平均 QPS） */
    @Value("${gateway.rate-limit.rps:20}")
    private double rps;

    /** 桶容量（允许的瞬时突发） */
    @Value("${gateway.rate-limit.burst:40}")
    private double burst;

    @Value("${gateway.rate-limit.path-prefix:/shop}")
    private String pathPrefix;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        // 在鉴权（-100）之后：白名单与已登录请求都要限流
        return -90;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getRawPath();
        if (!path.startsWith(pathPrefix)) {
            return chain.filter(exchange);
        }
        String key = clientIp(exchange);
        evictIfCrowded();
        if (!tryAcquire(key)) {
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    private boolean tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(rps, burst));
        return bucket.tryConsume(1.0);
    }

    private void evictIfCrowded() {
        if (buckets.size() < MAX_KEYS) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            if (now - entry.getValue().lastUsedAt > IDLE_EVICT_MILLIS) {
                it.remove();
            }
        }
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":429,\"msg\":\"请求过于频繁，请稍后重试\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** 单 IP 令牌桶：按时间差补充令牌，上限 capacity */
    static final class Bucket {
        private final double rate;
        private final double capacity;
        private double tokens;
        private long lastRefillNanos;
        private volatile long lastUsedAt;

        Bucket(double rate, double capacity) {
            this.rate = rate > 0 ? rate : 20.0;
            this.capacity = capacity > 0 ? capacity : 40.0;
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastUsedAt = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(double amount) {
            refill();
            lastUsedAt = System.currentTimeMillis();
            if (tokens >= amount) {
                tokens -= amount;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds <= 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + elapsedSeconds * rate);
            lastRefillNanos = now;
        }
    }
}
