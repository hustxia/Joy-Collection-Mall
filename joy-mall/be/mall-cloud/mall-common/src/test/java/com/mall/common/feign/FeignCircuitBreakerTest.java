package com.mall.common.feign;

import com.mall.common.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6 S-02 熔断与降级回归。
 *
 * <p>要守住的两条：
 * <ul>
 *   <li><b>熔断真的会打开</b>：下游连续失败到阈值后，调用不再打下游（不占线程、不拖时间），直接走降级；</li>
 *   <li><b>降级不伪造成功</b>：商品写操作（扣库存）降级必须抛 503 让事务回滚，绝不能返回 true 造成超卖；
 *       营销读接口则退化为「没有可用券」，下单主链不受影响。</li>
 * </ul>
 *
 * <p><b>注意熔断器粒度</b>：Spring Cloud OpenFeign 默认的 {@code CircuitBreakerNameResolver}
 * 会为「每个 Feign 方法」各建一个熔断器（实测名如 {@code GoodsInnerClientskuByIdsList}），
 * 而不是每个客户端一个。所以这里必须配 {@code resilience4j.circuitbreaker.configs.default}
 * （对所有实例生效），配 {@code instances.mall-goods} 是配不到实际使用的实例的。
 * 生产 yml 同样走 {@code configs.default}，口径一致。
 */
@SpringBootTest(classes = FeignCircuitBreakerTest.CircuitBreakerTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.openfeign.circuitbreaker.enabled=true",
                "resilience4j.circuitbreaker.configs.default.slidingWindowSize=4",
                "resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=4",
                "resilience4j.circuitbreaker.configs.default.failureRateThreshold=50",
                "resilience4j.circuitbreaker.configs.default.waitDurationInOpenState=30s",
                "spring.cloud.openfeign.client.config.default.connectTimeout=1000",
                "spring.cloud.openfeign.client.config.default.readTimeout=2000"
        })
class FeignCircuitBreakerTest {

    /** 降级实现是 @Component，测试上下文必须扫到 feign 包，否则 Feign 找不到 fallback 实例 */
    @SpringBootConfiguration
    @ComponentScan("com.mall.common.feign.fallback")
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableFeignClients(basePackages = "com.mall.common.feign")
    static class CircuitBreakerTestApp {
    }

    /** 阈值：连续失败次数等于窗口大小，第 5 次调用时熔断器应为 OPEN */
    private static final int THRESHOLD = 4;

    private static final StubDownstream stub = createStub();

    @Autowired
    private GoodsInnerClient goodsInnerClient;
    @Autowired
    private MarketingInnerClient marketingInnerClient;

    private static StubDownstream createStub() {
        try {
            return new StubDownstream();
        } catch (IOException e) {
            throw new IllegalStateException("假下游启动失败", e);
        }
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String url = stub.url();
        registry.add("feign.clients.mall-goods", () -> url);
        registry.add("feign.clients.mall-marketing", () -> url);
        registry.add("feign.clients.mall-order", () -> url);
        registry.add("feign.clients.mall-member", () -> url);
        registry.add("feign.clients.mall-refund", () -> url);
    }

    /**
     * 连续失败打满窗口 → 熔断打开 → 不再请求下游，降级抛 503 可读文案。
     *
     * <p>断言口径：resilience4j 的 COUNT_BASED 滑窗在「刚好打满窗口」那一次才会转 OPEN，
     * 因此 OPEN 态之前的真实下发次数允许是 THRESHOLD 或 THRESHOLD+1（取决于窗口转态的时机）。
     * 真正要守住的不变式是：<b>熔断稳定后，连续调用不得再打下游</b>（至多一次 OPEN 态探测）。
     * 下面用「稳态调用后的净新增 <= 1」来表达这条不变式，比死盯精确计数更稳，也能在
     * 「熔断根本没打开」时（净新增会持续累加）当场失败。
     */
    @Test
    void goodsCircuitOpensAndFailsFast() {
        stub.reset();
        stub.respondStatus(503, "{\"error\":\"unavailable\"}");

        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(Exception.class, () -> goodsInnerClient.skuByIds(List.of(3001L)),
                    "下游 503 应抛异常（第 " + (i + 1) + " 次）");
        }
        int beforeSteady = stub.count();
        assertTrue(beforeSteady >= THRESHOLD,
                "熔断打开前至少应有 " + THRESHOLD + " 次真实请求，实际 " + beforeSteady);

        // 稳态：熔断打开后连续多次调用，均走降级，且几乎不再下发到下游
        BusinessException ex = null;
        for (int i = 0; i < 6; i++) {
            ex = assertThrows(BusinessException.class,
                    () -> goodsInnerClient.skuByIds(List.of(3001L)), "熔断后应走降级");
        }
        assertEquals(503, ex.getCode(), "降级码必须是 503");
        assertTrue(ex.getMessage().contains("商品服务暂不可用"), "降级文案要可读：" + ex.getMessage());

        int afterSteady = stub.count();
        assertTrue(afterSteady - beforeSteady <= 1,
                "熔断稳定后不得再打下游，实际又发了 " + (afterSteady - beforeSteady) + " 次");
    }

    /** 扣库存降级绝不返回 true（否则订单以为扣成功 → 超卖） */
    @Test
    void deductStockFallbackNeverFakesSuccess() {
        stub.reset();
        stub.respondStatus(503, "{\"error\":\"unavailable\"}");

        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(Exception.class, () -> goodsInnerClient.deductStock(Map.of(3001L, 1)));
        }
        int beforeSteady = stub.count();
        assertTrue(beforeSteady >= THRESHOLD,
                "熔断打开前至少应有 " + THRESHOLD + " 次真实请求，实际 " + beforeSteady);

        BusinessException ex = null;
        for (int i = 0; i < 6; i++) {
            ex = assertThrows(BusinessException.class,
                    () -> goodsInnerClient.deductStock(Map.of(3001L, 1)), "熔断后扣库存必须抛异常");
        }
        assertEquals(503, ex.getCode(), "扣库存降级码必须是 503（绝不伪造成功）");

        int afterSteady = stub.count();
        assertTrue(afterSteady - beforeSteady <= 1,
                "熔断稳定后不得再发扣减请求，实际又发了 " + (afterSteady - beforeSteady) + " 次");
    }

    /** 营销读接口降级成「没有可用券」，下单主链不受阻 */
    @Test
    void marketingReadDegradesToEmpty() {
        stub.reset();
        stub.respondStatus(503, "{\"error\":\"unavailable\"}");

        // 读接口有 fallback，失败时本身就返回空列表（不抛异常）
        for (int i = 0; i < THRESHOLD; i++) {
            List<?> coupons = marketingInnerClient.availableRecords(9L);
            assertTrue(coupons.isEmpty(), "营销读失败应降级为空列表（第 " + (i + 1) + " 次）");
        }
        int beforeSteady = stub.count();
        assertTrue(beforeSteady >= THRESHOLD,
                "熔断打开前至少应有 " + THRESHOLD + " 次真实请求，实际 " + beforeSteady);

        // 稳态：读接口降级为空列表，且不再打营销服务
        for (int i = 0; i < 6; i++) {
            List<?> coupons = marketingInnerClient.availableRecords(9L);
            assertTrue(coupons.isEmpty(), "熔断后可用券读仍应降级为空列表（第 " + (i + 1) + " 次）");
        }

        int afterSteady = stub.count();
        assertTrue(afterSteady - beforeSteady <= 1,
                "熔断打开后不得再打营销服务，实际又发了 " + (afterSteady - beforeSteady) + " 次");
    }
}
