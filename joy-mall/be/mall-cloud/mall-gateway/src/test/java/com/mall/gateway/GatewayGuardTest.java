package com.mall.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V6 S-03 网关防护单测：白名单 / inner 拦截 / 401 / 身份注入 / 限流 429。
 * 不启动 Spring 上下文，避免依赖真实 DB。
 */
class GatewayGuardTest {

    @Test
    void innerEndpointAlwaysForbidden() {
        GatewayAuthFilter filter = newFilter(identity -> null);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/inner/sku/deduct").build());
        filter.filter(exchange, chain()).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void anonymousWhitelistPasses() {
        GatewayAuthFilter filter = newFilter(identity -> null);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/shop/goods").build());
        AtomicInteger passed = new AtomicInteger();
        filter.filter(exchange, e -> {
            passed.incrementAndGet();
            return Mono.empty();
        }).block();
        assertEquals(1, passed.get());
        assertEquals(null, exchange.getResponse().getStatusCode());
    }

    @Test
    void writeWithoutTokenIs401() {
        GatewayAuthFilter filter = newFilter(identity -> null);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/shop/cart/add").build());
        filter.filter(exchange, chain()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void invalidTokenIs401() {
        GatewayAuthFilter filter = newFilter(identity -> null);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/shop/cart/list").header("Authorization", "Bearer bad-token").build());
        filter.filter(exchange, chain()).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validTokenInjectsIdentityHeaders() {
        GatewayTokenResolver.Identity identity = new GatewayTokenResolver.Identity("m:1001", "1001", "member");
        GatewayAuthFilter filter = newFilter(token -> identity);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/shop/cart/list").header("Authorization", "Bearer ok-token").build());
        ServerWebExchange[] seen = new ServerWebExchange[1];
        filter.filter(exchange, e -> {
            seen[0] = e;
            return Mono.empty();
        }).block();
        assertNotNull(seen[0]);
        assertEquals("1001", seen[0].getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("member", seen[0].getRequest().getHeaders().getFirst("X-User-Type"));
        assertEquals("m:1001", seen[0].getRequest().getHeaders().getFirst("X-Username"));
    }

    @Test
    void rateLimitRejectsAfterBurst() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "rps", 1.0);
        ReflectionTestUtils.setField(filter, "burst", 5.0);
        ReflectionTestUtils.setField(filter, "pathPrefix", "/shop");

        int rejected = 0;
        for (int i = 0; i < 10; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/shop/goods")
                            .header("X-Forwarded-For", "10.0.0.1")
                            .build());
            filter.filter(exchange, e -> Mono.empty()).block();
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                rejected++;
            }
        }
        // 桶容量 5，连打 10 次至少 4 次被拒（瞬时补充的少量令牌允许 ±1）
        assertTrue(rejected >= 4, "超出 burst 后应 429，实际拒绝 " + rejected);
    }

    @Test
    void rateLimitIgnoresNonShopPaths() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "rps", 1.0);
        ReflectionTestUtils.setField(filter, "burst", 1.0);
        ReflectionTestUtils.setField(filter, "pathPrefix", "/shop");

        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/mall_goods/list").build());
            filter.filter(exchange, e -> Mono.empty()).block();
            assertEquals(null, exchange.getResponse().getStatusCode());
        }
    }

    // ===== 辅助 =====

    private GatewayAuthFilter newFilter(java.util.function.Function<String, GatewayTokenResolver.Identity> resolver) {
        GatewayTokenResolver mocked = mock(GatewayTokenResolver.class);
        when(mocked.resolve(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> resolver.apply(inv.getArgument(0)));
        GatewayAuthFilter filter = new GatewayAuthFilter();
        ReflectionTestUtils.setField(filter, "tokenResolver", mocked);
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "extraWhitelist", "");
        return filter;
    }

    private org.springframework.cloud.gateway.filter.GatewayFilterChain chain() {
        return e -> Mono.empty();
    }
}
