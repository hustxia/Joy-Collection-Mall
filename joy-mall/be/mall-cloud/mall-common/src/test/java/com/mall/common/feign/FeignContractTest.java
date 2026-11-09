package com.mall.common.feign;

import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.CartBrief;
import com.mall.common.feign.dto.CouponBrief;
import com.mall.common.feign.dto.CouponRecordBrief;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.OrderLogAppendDTO;
import com.mall.common.feign.dto.OrderLogBrief;
import com.mall.common.feign.dto.RefundBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.sun.net.httpserver.HttpServer;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6 T-03 Feign 契约测试：22 个 {@code /inner} 端点逐个对「假下游」断言
 * <b>HTTP 方法 + 路径 + 参数/请求体 + 裸对象反序列化</b>。
 *
 * <p>为什么必须测：{@code /inner} 的硬规则是「返回裸对象、不包 Result」，且客户端签名与
 * 服务端签名分居两个模块。改一边没改另一边，编译照样过、上线才炸。这里任何一侧改签名
 * （换路径、换参数名、加 Result 包装）都会立刻红。
 *
 * <p>假下游用 JDK 自带 {@code HttpServer}，不引 WireMock/MockWebServer —— 契约测试不该
 * 为了一个桩再拉一套框架依赖，也保证离线可跑。
 */
@SpringBootTest(classes = FeignContractTest.ContractTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FeignContractTest {

    /** 只装配 Feign 客户端，不碰数据源（mall-common 自带 jdbc 依赖，必须排除 DataSource 自动配置） */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableFeignClients(basePackages = "com.mall.common.feign")
    static class ContractTestApp {
    }

    /** 静态初始化：上下文创建（@DynamicPropertySource）早于 @BeforeAll，桩必须更早起来 */
    private static final StubDownstream stub = createStub();

    @Autowired
    private GoodsInnerClient goodsInnerClient;
    @Autowired
    private OrderInnerClient orderInnerClient;
    @Autowired
    private MemberInnerClient memberInnerClient;
    @Autowired
    private MarketingInnerClient marketingInnerClient;
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
    static void feignTargets(DynamicPropertyRegistry registry) {
        String url = stub.url();
        registry.add("feign.clients.mall-goods", () -> url);
        registry.add("feign.clients.mall-order", () -> url);
        registry.add("feign.clients.mall-member", () -> url);
        registry.add("feign.clients.mall-marketing", () -> url);
        registry.add("feign.clients.mall-refund", () -> url);
    }

    @BeforeEach
    void reset() {
        stub.reset();
    }

    /** 商品域 6 个端点：读 SKU/商品 + 扣减/回补/销量三个写操作 */
    @Test
    void goodsClientContracts() throws Exception {
        stub.respond("[{\"id\":3001,\"goodsId\":2001,\"skuCode\":\"SKU-1\",\"price\":100,\"stock\":7,\"status\":1}]");
        List<SkuBrief> skus = goodsInnerClient.skuByIds(List.of(3001L, 3002L));
        assertRequest("GET", "/inner/sku/by-ids");
        assertTrue(stub.last().query().contains("ids=3001"), "ids 必须以 query 传递：" + stub.last().query());
        assertEquals(3001L, skus.get(0).getId(), "必须能反序列化裸数组");
        assertEquals(7, skus.get(0).getStock());

        stub.respond("[{\"id\":2001,\"name\":\"商品\",\"priceMin\":100,\"priceMax\":200,\"status\":1}]");
        List<GoodsBrief> goods = goodsInnerClient.goodsByIds(List.of(2001L));
        assertRequest("GET", "/inner/goods/by-ids");
        assertEquals("商品", goods.get(0).getName(), "裸对象反序列化");

        stub.respond("true");
        assertEquals(Boolean.TRUE, goodsInnerClient.deductStock(Map.of(3001L, 2)), "扣库存返回裸 boolean");
        assertRequest("POST", "/inner/sku/deduct");
        assertEquals("{\"3001\":2}", stub.last().body(), "请求体是 {skuId: 数量} 的 JSON");

        stub.respondEmpty();
        goodsInnerClient.restoreStock(Map.of(3001L, 2));
        assertRequest("POST", "/inner/sku/restore");
        assertEquals("{\"3001\":2}", stub.last().body());

        goodsInnerClient.incrementSkuSales(Map.of(3001L, 3));
        assertRequest("POST", "/inner/sku/sales");

        goodsInnerClient.incrementGoodsSales(Map.of(2001L, 3));
        assertRequest("POST", "/inner/goods/sales");
        assertEquals("{\"2001\":3}", stub.last().body());
    }

    /** 订单域 4 个端点 */
    @Test
    void orderClientContracts() throws Exception {
        stub.respond("{\"id\":5001,\"orderNo\":\"M001\",\"memberId\":9,\"status\":30,\"payAmount\":200}");
        OrderBrief order = orderInnerClient.order(5001L);
        assertRequest("GET", "/inner/order/5001");
        assertEquals("M001", order.getOrderNo());
        assertEquals(30, order.getStatus());

        stub.respond("[{\"id\":6001,\"orderId\":5001,\"goodsId\":2001,\"skuId\":3001,\"quantity\":2,\"price\":100}]");
        List<OrderItemBrief> items = orderInnerClient.orderItems(5001L);
        assertRequest("GET", "/inner/order-item");
        assertTrue(stub.last().query().contains("orderId=5001"), "orderId 走 query");
        assertEquals(2, items.get(0).getQuantity());

        stub.respond("[{\"id\":7001,\"orderId\":5001,\"content\":\"提交订单\",\"operatorType\":1}]");
        List<OrderLogBrief> logs = orderInnerClient.orderLogs(5001L);
        assertRequest("GET", "/inner/order-log");
        assertEquals("提交订单", logs.get(0).getContent());

        stub.respondEmpty();
        OrderLogAppendDTO dto = new OrderLogAppendDTO();
        dto.setOrderId(5001L);
        dto.setOrderNo("M001");
        dto.setContent("售后退款完成");
        dto.setOperator("admin");
        dto.setOperatorType(2);
        orderInnerClient.appendLog(dto);
        assertRequest("POST", "/inner/order-log");
        assertTrue(stub.last().body().contains("\"content\":\"售后退款完成\""), "日志体走 JSON body：" + stub.last().body());
    }

    /** 会员域 5 个端点 */
    @Test
    void memberClientContracts() throws Exception {
        stub.respond("{\"id\":11,\"memberId\":9,\"receiverName\":\"张三\",\"province\":\"浙江省\",\"city\":\"杭州市\"}");
        AddressBrief address = memberInnerClient.address(11L);
        assertRequest("GET", "/inner/address/11");
        assertEquals("张三", address.getReceiverName());

        stub.respond("[{\"id\":21,\"memberId\":9,\"skuId\":3001,\"num\":2,\"checked\":1}]");
        List<CartBrief> carts = memberInnerClient.cartList(9L, 1);
        assertRequest("GET", "/inner/cart");
        assertTrue(stub.last().query().contains("memberId=9"));
        assertTrue(stub.last().query().contains("checked=1"));
        assertEquals(2, carts.get(0).getNum());

        stub.respond("{\"id\":9,\"nickname\":\"会员\",\"phone\":\"19900000000\"}");
        MemberBrief member = memberInnerClient.member(9L);
        assertRequest("GET", "/inner/member/9");
        assertEquals("会员", member.getNickname());

        stub.respond("[{\"id\":9,\"nickname\":\"会员\"}]");
        assertEquals(1, memberInnerClient.membersByIds(List.of(9L)).size());
        assertRequest("GET", "/inner/member/by-ids");

        stub.respondEmpty();
        memberInnerClient.clearCart(9L, List.of(21L, 22L));
        assertRequest("POST", "/inner/cart/clear");
        assertTrue(stub.last().queryOrBody().contains("memberId=9"), "清车参数 memberId 必须传：" + stub.last().queryOrBody());
    }

    /** 营销域 5 个端点（含三个写：锁券/核销/释放） */
    @Test
    void marketingClientContracts() throws Exception {
        stub.respond("[{\"id\":41,\"couponId\":51,\"memberId\":9,\"status\":1}]");
        List<CouponRecordBrief> records = marketingInnerClient.availableRecords(9L);
        assertRequest("GET", "/inner/coupon-record");
        assertEquals(51L, records.get(0).getCouponId());

        stub.respond("[{\"id\":51,\"name\":\"满减券\",\"type\":1,\"amount\":30,\"thresholdAmount\":100,\"status\":1}]");
        List<CouponBrief> coupons = marketingInnerClient.couponByIds(List.of(51L));
        assertRequest("GET", "/inner/coupon/by-ids");
        assertEquals(30L, coupons.get(0).getAmount());

        stub.respond("true");
        assertEquals(Boolean.TRUE, marketingInnerClient.lockRecord(41L, 5001L), "锁券返回裸 boolean");
        assertRequest("POST", "/inner/coupon-record/lock");
        assertTrue(stub.last().queryOrBody().contains("recordId=41"));
        assertTrue(stub.last().queryOrBody().contains("orderId=5001"));

        stub.respondEmpty();
        marketingInnerClient.useByOrder(5001L);
        assertRequest("POST", "/inner/coupon-record/use-by-order");
        assertTrue(stub.last().queryOrBody().contains("orderId=5001"));

        marketingInnerClient.releaseByOrder(5001L);
        assertRequest("POST", "/inner/coupon-record/release-by-order");
    }

    /** 售后域 2 个端点 */
    @Test
    void refundClientContracts() throws Exception {
        stub.respond("[{\"id\":61,\"orderId\":5001,\"status\":1,\"refundNo\":\"RF1\"}]");
        List<RefundBrief> latest = refundInnerClient.latestByOrderIds(List.of(5001L, 5002L));
        assertRequest("GET", "/inner/by-order-ids");
        assertTrue(stub.last().query().contains("ids=5001"));
        assertEquals("RF1", latest.get(0).getRefundNo());

        stub.respond("{\"id\":61,\"orderId\":5001,\"status\":4}");
        RefundBrief one = refundInnerClient.latestByOrderId(5001L);
        assertRequest("GET", "/inner/latest-by-order/5001");
        assertEquals(4, one.getStatus());
    }

    /** 全部 /inner 端点都不带 Authorization（FeignConfig 的过滤规则），否则下游会把会员令牌当越权拒绝 */
    @Test
    void innerCallsCarryNoAuthorizationHeader() {
        stub.respond("[]");
        goodsInnerClient.skuByIds(List.of(3001L));
        goodsInnerClient.goodsByIds(List.of(2001L));
        stub.respond("true");
        goodsInnerClient.deductStock(Map.of(3001L, 1));

        for (StubDownstream.Recorded recorded : stub.all()) {
            assertTrue(recorded.authorization() == null,
                    "/inner 调用不得透传 Authorization，实际：" + recorded.path() + " -> " + recorded.authorization());
        }
    }

    private void assertRequest(String method, String path) {
        StubDownstream.Recorded recorded = stub.last();
        assertNotNull(recorded, "假下游没收到请求：" + method + " " + path);
        assertEquals(method, recorded.method(), "HTTP 方法不符：" + path);
        assertEquals(path, recorded.path(), "路径不符，实际请求：" + recorded.method() + " " + recorded.path());
    }
}
