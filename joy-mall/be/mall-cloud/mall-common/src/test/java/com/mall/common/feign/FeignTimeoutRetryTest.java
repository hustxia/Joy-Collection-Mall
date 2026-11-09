package com.mall.common.feign;

import com.mall.common.feign.dto.RefundBrief;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6 S-01 超时与重试策略回归。
 *
 * <p>三条要守住的规则：
 * <ol>
 *   <li><b>必须超时</b>：下游慢 2 秒、我方读超时 600ms，调用要在 ~600ms 断开，而不是傻等到天荒地老；</li>
 *   <li><b>写操作不重试</b>：扣库存遇到 503 只发一次请求（重试会把「库存不足」放大成重复扣减）；</li>
 *   <li><b>读操作可重试</b>：售后查询这类纯 GET 客户端允许重试，503 下能打出 3 次尝试。</li>
 * </ol>
 */
@SpringBootTest(classes = FeignTimeoutRetryTest.TimeoutTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FeignTimeoutRetryTest {

    private static final long READ_TIMEOUT_MS = 600L;

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableFeignClients(basePackages = "com.mall.common.feign")
    static class TimeoutTestApp {
    }

    private static final StubDownstream stub = createStub();

    @Autowired
    private GoodsInnerClient goodsInnerClient;
    @Autowired
    private RefundInnerClient refundInnerClient;

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
        registry.add("feign.clients.mall-refund", () -> url);
        registry.add("spring.cloud.openfeign.client.config.default.connectTimeout", () -> "500");
        registry.add("spring.cloud.openfeign.client.config.default.readTimeout", () -> String.valueOf(READ_TIMEOUT_MS));
    }

    @BeforeEach
    void reset() {
        stub.reset();
    }

    /** 慢下游：读超时到点即断，耗时远小于下游的 3 秒 */
    @Test
    void slowDownstreamIsCutByReadTimeout() {
        stub.respond("[]");
        stub.delay(3000);

        long startedAt = System.currentTimeMillis();
        assertThrows(Exception.class, () -> goodsInnerClient.skuByIds(java.util.List.of(3001L)),
                "下游 3s 未响应，必须抛超时异常");
        long elapsed = System.currentTimeMillis() - startedAt;

        assertTrue(elapsed < 2000, "应在读超时（" + READ_TIMEOUT_MS + "ms）附近断开，实际耗时 " + elapsed + "ms");
    }

    /** 写操作不重试：503 只打一次（否则扣库存会被重复执行） */
    @Test
    void writeIsNeverRetried() {
        stub.respondStatus(503, "{\"error\":\"unavailable\"}");

        assertThrows(Exception.class, () -> goodsInnerClient.deductStock(Map.of(3001L, 1)),
                "503 应抛异常");
        assertEquals(1, stub.count(), "扣库存只允许一次请求，实际 " + stub.count() + " 次");
    }

    /** 纯读客户端可重试：503 下打出 3 次尝试（首次 + 2 次重试） */
    @Test
    void readClientRetriesOnServerError() {
        stub.respondStatus(503, "{\"error\":\"unavailable\"}");

        assertThrows(Exception.class, () -> refundInnerClient.latestByOrderId(5001L), "503 最终仍应失败");
        assertEquals(3, stub.count(), "读客户端配置了 3 次尝试，实际 " + stub.count() + " 次");
    }

    /** 读客户端重试期间一旦成功就返回，不再继续打 */
    @Test
    void readClientStopsAfterSuccess() {
        stub.respond("{\"id\":61,\"orderId\":5001,\"status\":4}");

        RefundBrief brief = refundInnerClient.latestByOrderId(5001L);
        assertEquals(4, brief.getStatus());
        assertEquals(1, stub.count(), "成功即返回，不做多余重试");
    }
}
