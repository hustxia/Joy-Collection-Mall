package com.mall.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MarketingInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.RefundInnerClient;
import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.FreightQuoteResult;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.MerchantBrief;
import com.mall.common.feign.dto.RefundBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.pay.SandboxPayChannel;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V12 交易真实化集成测试：运费入单、预支付 + 回调幂等、关单后退款、部分发货、发票。
 *
 * <p>边界口径同 V6：订单域只断言自己的表（{@code mall_order / mall_payment / mall_delivery /
 * mall_invoice}）与发出的 Feign 调用参数；商品域运费算法由
 * {@code FreightTemplateFlowTest} 与 {@code FreightCalculatorTest} 负责。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V12TradeFlowTest extends MallIntegrationTestBase {

    @MockBean
    private GoodsInnerClient goodsInnerClient;
    @MockBean
    private MemberInnerClient memberInnerClient;
    @MockBean
    private MarketingInnerClient marketingInnerClient;
    @MockBean
    private RefundInnerClient refundInnerClient;
    @MockBean
    private MerchantInnerClient merchantInnerClient;

    @Autowired
    private SandboxPayChannel sandboxPayChannel;

    private long memberId;
    private long addressId;
    private String token;
    private String admin;

    private final Map<Long, SkuBrief> skuStubs = new HashMap<>();
    private final Map<Long, GoodsBrief> goodsStubs = new HashMap<>();
    private long goodsId;
    private long skuId;

    @BeforeAll
    void prepareFixtures() {
        memberId = createMember("V12");
        addressId = createAddress(memberId);
        token = memberToken(memberId);
        admin = adminToken();
        goodsId = createGoods(createCategory(), "V12商品", 100L);
        skuId = createSku(goodsId, 100L, 100);
    }

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    @BeforeEach
    void stubDownstream() {
        skuStubs.clear();
        goodsStubs.clear();
        stubSku(skuId, goodsId, 100L, 100);

        when(goodsInnerClient.skuByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(skuStubs::get).filter(Objects::nonNull).toList();
        });
        when(goodsInnerClient.goodsByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(goodsStubs::get).filter(Objects::nonNull).toList();
        });
        when(goodsInnerClient.deductStock(anyMap())).thenReturn(true);
        // 默认包邮；需要运费的用例单独 stub
        when(goodsInnerClient.freightQuote(any())).thenReturn(freight(0L));

        when(memberInnerClient.address(anyLong())).thenAnswer(inv -> addressOf(inv.getArgument(0)));
        when(memberInnerClient.cartList(anyLong(), any())).thenReturn(List.of());
        when(memberInnerClient.member(anyLong())).thenAnswer(inv -> memberOf(inv.getArgument(0)));

        when(marketingInnerClient.availableRecords(anyLong())).thenReturn(List.of());
        when(marketingInnerClient.lockRecord(anyLong(), anyLong())).thenReturn(true);
        when(refundInnerClient.latestByOrderIds(anyList())).thenReturn(List.of());
        when(refundInnerClient.latestByOrderId(anyLong())).thenReturn(null);

        when(merchantInnerClient.merchant(anyLong())).thenAnswer(inv -> {
            MerchantBrief brief = new MerchantBrief();
            brief.setId(inv.getArgument(0));
            brief.setName("测试店铺-" + inv.getArgument(0));
            brief.setStatus(1);
            return brief;
        });
        when(merchantInnerClient.settle(anyLong(), anyLong(), any(), anyLong())).thenReturn(true);
    }

    // ===== 1. 运费 =====

    @Test
    @DisplayName("运费入单：pay_amount = 商品额 − 优惠 + 运费，确认页与落库一致")
    void freightIncludedInPayAmount() throws Exception {
        when(goodsInnerClient.freightQuote(any())).thenReturn(freight(1700L));

        JsonNode confirm = ok(post("/shop/order/confirm").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":" + skuId + ",\"num\":3,\"addressId\":" + addressId + "}"));
        assertEquals(1700L, confirm.get("data").get("freightAmount").asLong(), "确认页要显示运费");
        assertEquals(300L + 1700L, confirm.get("data").get("payAmount").asLong(), "实付含运费");

        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":3}"));
        long orderId = Long.parseLong(submit.get("data").get("orderId").asText());
        assertEquals(300L + 1700L, submit.get("data").get("payAmount").asLong());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT total_amount, freight_amount, discount_amount, pay_amount FROM mall_order WHERE id = ?", orderId);
        assertEquals(1700L, ((Number) row.get("freight_amount")).longValue());
        assertEquals(((Number) row.get("total_amount")).longValue()
                        - ((Number) row.get("discount_amount")).longValue()
                        + ((Number) row.get("freight_amount")).longValue(),
                ((Number) row.get("pay_amount")).longValue(), "金额恒等式必须成立");
    }

    // ===== 2. 预支付与回调幂等 =====

    @Test
    @DisplayName("回调幂等：同一回调重放 10 次，订单只推进一次、支付单只有一行")
    void notifyIsIdempotent() throws Exception {
        String orderNo = submitOrder(1);
        JsonNode prepay = ok(post("/shop/pay/prepay").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\",\"channel\":\"sandbox\"}"));
        String payNo = prepay.get("data").get("payNo").asText();
        long amount = prepay.get("data").get("amount").asLong();
        assertEquals(100L, amount);
        assertTrue(prepay.get("data").get("params").get("qrCode").asText().contains(payNo), "需返回可渲染的支付参数");

        String tradeNo = "SANDBOX-T-" + System.nanoTime();
        String body = notifyBody(payNo, tradeNo, amount, 1, true);
        int replayCount = 0;
        for (int i = 0; i < 10; i++) {
            JsonNode resp = ok(post("/shop/pay/notify/sandbox")
                    .contentType(MediaType.APPLICATION_JSON).content(body));
            if (resp.get("data").get("replay").asBoolean()) {
                replayCount++;
            }
        }
        assertEquals(9, replayCount, "首次推进，其余 9 次按重放返回");
        assertEquals(20, jdbc.queryForObject("SELECT status FROM mall_order WHERE order_no = ?",
                Integer.class, orderNo));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mall_payment WHERE order_no = ?",
                Integer.class, orderNo));
        assertEquals(1, jdbc.queryForObject(
                "SELECT status FROM mall_payment WHERE order_no = ?", Integer.class, orderNo));
    }

    @Test
    @DisplayName("回调风控：验签失败与金额不一致都必须 400，且不推进订单")
    void notifyRejectsBadSignAndAmount() throws Exception {
        String orderNo = submitOrder(1);
        JsonNode prepay = ok(post("/shop/pay/prepay").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\",\"channel\":\"sandbox\"}"));
        String payNo = prepay.get("data").get("payNo").asText();

        bizError(post("/shop/pay/notify/sandbox").contentType(MediaType.APPLICATION_JSON)
                .content(notifyBody(payNo, "T-BADSIGN", 100L, 1, false)), 400);
        bizError(post("/shop/pay/notify/sandbox").contentType(MediaType.APPLICATION_JSON)
                .content(notifyBody(payNo, "T-BADAMOUNT", 999L, 1, true)), 400);
        assertEquals(10, jdbc.queryForObject("SELECT status FROM mall_order WHERE order_no = ?",
                Integer.class, orderNo), "订单不应被推进");
    }

    @Test
    @DisplayName("未配置渠道：微信/支付宝直接 503，不返回假支付参数")
    void unconfiguredChannelRejected() throws Exception {
        String orderNo = submitOrder(1);
        bizError(post("/shop/pay/prepay").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\",\"channel\":\"wxpay\"}"), 503);
    }

    // ===== 3. 关单后回调自动退款 =====

    @Test
    @DisplayName("关单后到账：不复活订单，自动原路退款并落终态退款单")
    void closedOrderNotifyAutoRefunds() throws Exception {
        String orderNo = submitOrder(1);
        JsonNode prepay = ok(post("/shop/pay/prepay").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\",\"channel\":\"sandbox\"}"));
        String payNo = prepay.get("data").get("payNo").asText();
        long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);

        // 先取消（模拟超时关单），再收到成功回调
        ok(post("/shop/order/cancel").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + "}"));

        RefundBrief brief = new RefundBrief();
        brief.setId(1L);
        brief.setRefundNo("RF-AUTO-TEST");
        brief.setOrderId(orderId);
        when(refundInnerClient.autoRefund(any())).thenReturn(brief);

        ok(post("/shop/pay/notify/sandbox").contentType(MediaType.APPLICATION_JSON)
                .content(notifyBody(payNo, "T-CLOSED", 100L, 1, true)));

        verify(refundInnerClient, times(1)).autoRefund(any());
        assertEquals(50, jdbc.queryForObject("SELECT status FROM mall_order WHERE id = ?", Integer.class, orderId),
                "订单保持已取消，不能被支付回调复活");
        assertEquals(3, jdbc.queryForObject("SELECT status FROM mall_payment WHERE pay_no = ?",
                Integer.class, payNo), "支付单应置已退款");
    }

    // ===== 4. 部分发货 =====

    @Test
    @DisplayName("部分发货：只发 1 件订单仍待发货；发完后转 30，且形成两个包裹")
    void partialShipment() throws Exception {
        String orderNo = submitOrder(2);
        payByMock(orderNo);
        long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);
        long itemId = jdbc.queryForObject("SELECT id FROM mall_order_item WHERE order_id = ?", Long.class, orderId);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("orderId", orderId);
        first.put("expressCompany", "顺丰速运");
        first.put("expressNo", "SF" + System.nanoTime());
        first.put("items", List.of(Map.of("orderItemId", itemId, "quantity", 1)));
        JsonNode shipped = ok(post("/mall_delivery/ship").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(first)));
        assertFalse(shipped.get("data").get("allShipped").asBoolean());
        assertEquals(20, jdbc.queryForObject("SELECT status FROM mall_order WHERE id = ?", Integer.class, orderId),
                "未发完时订单仍是待发货");
        assertEquals(1, jdbc.queryForObject("SELECT shipped_quantity FROM mall_order_item WHERE id = ?",
                Integer.class, itemId));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("orderId", orderId);
        second.put("expressCompany", "圆通速递");
        second.put("expressNo", "YT" + System.nanoTime());
        JsonNode rest = ok(post("/mall_delivery/ship").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(second)));
        assertTrue(rest.get("data").get("allShipped").asBoolean());
        assertEquals(30, jdbc.queryForObject("SELECT status FROM mall_order WHERE id = ?", Integer.class, orderId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mall_delivery WHERE order_id = ?",
                Integer.class, orderId));

        JsonNode track = ok(get("/shop/delivery/track/" + orderId).header("Authorization", bearer(token)));
        assertEquals(2, track.get("data").get("packages").size(), "两个包裹都要能查");
        assertTrue(track.get("data").get("packages").get(0).get("nodes").size() >= 1, "至少一条轨迹节点");

        // 单号重复必须挡住（防止一笔运单发两个订单）
        bizError(post("/mall_delivery/ship").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(second)), 400);
    }

    @Test
    @DisplayName("发货单号重复：同单号第二次发货被拒")
    void duplicateExpressNoRejected() throws Exception {
        String orderNo = submitOrder(1);
        payByMock(orderNo);
        long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);
        String expressNo = "JD" + System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("expressCompany", "京东物流");
        body.put("expressNo", expressNo);
        ok(post("/mall_delivery/ship").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));

        String anotherOrder = submitOrder(1);
        payByMock(anotherOrder);
        long anotherId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, anotherOrder);
        Map<String, Object> dup = new LinkedHashMap<>();
        dup.put("orderId", anotherId);
        dup.put("expressCompany", "京东物流");
        dup.put("expressNo", expressNo);
        bizError(post("/mall_delivery/ship").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(dup)), 400);
    }

    // ===== 5. 发票 =====

    @Test
    @DisplayName("发票：企业抬头必须合法税号，一单一票，开票后可红冲")
    void invoiceApplyIssueAndRedFlush() throws Exception {
        long orderId = finishedOrder();

        bizError(post("/shop/invoice/apply").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"titleType\":2,\"title\":\"杭州某某科技有限公司\"}"), 400);
        bizError(post("/shop/invoice/apply").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"titleType\":2,\"title\":\"杭州某某科技有限公司\","
                        + "\"taxNo\":\"123\"}"), 400);

        JsonNode applied = ok(post("/shop/invoice/apply").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"titleType\":2,\"title\":\"杭州某某科技有限公司\","
                        + "\"taxNo\":\"91330100MA27XYZ12A\"}"));
        String invoiceNo = applied.get("data").get("invoiceNo").asText();
        long invoiceId = jdbc.queryForObject("SELECT id FROM mall_invoice WHERE invoice_no = ?",
                Long.class, invoiceNo);
        assertEquals(jdbc.queryForObject("SELECT pay_amount FROM mall_order WHERE id = ?", Long.class, orderId),
                jdbc.queryForObject("SELECT amount FROM mall_invoice WHERE id = ?", Long.class, invoiceId),
                "开票金额必须取订单实付");

        bizError(post("/shop/invoice/apply").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"titleType\":1,\"title\":\"张三\"}"), 400);

        bizError(post("/mall_invoice/issue").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + invoiceId + "}"), 400);
        ok(post("/mall_invoice/issue").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + invoiceId + ",\"fileUrl\":\"/uploads/invoice/" + invoiceNo + ".pdf\"}"));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM mall_invoice WHERE id = ?", Integer.class, invoiceId));

        ok(post("/mall_invoice/red-flush").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + invoiceId + ",\"remark\":\"订单退款\"}"));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, red_flush_time FROM mall_invoice WHERE id = ?", invoiceId);
        assertEquals(2, ((Number) row.get("status")).intValue());
        assertTrue(row.get("red_flush_time") != null, "红冲必须记录时间");
    }

    @Test
    @DisplayName("发票：未完成订单不能申请开票")
    void invoiceRequiresFinishedOrder() throws Exception {
        String orderNo = submitOrder(1);
        payByMock(orderNo);
        long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);
        bizError(post("/shop/invoice/apply").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"titleType\":1,\"title\":\"张三\"}"), 400);
    }

    // ===== 辅助 =====

    private String submitOrder(int num) throws Exception {
        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":" + num + "}"));
        return submit.get("data").get("orderNo").asText();
    }

    private void payByMock(String orderNo) throws Exception {
        ok(post("/shop/pay/mock").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\"}"));
    }

    /** 造一个已完成订单（提交 → 支付 → 发货 → 确认收货） */
    private long finishedOrder() throws Exception {
        String orderNo = submitOrder(1);
        payByMock(orderNo);
        long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);
        Map<String, Object> ship = new LinkedHashMap<>();
        ship.put("orderId", orderId);
        ship.put("expressCompany", "中通快递");
        ship.put("expressNo", "ZT" + System.nanoTime());
        ok(post("/mall_delivery/ship").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(ship)));
        ok(post("/shop/order/confirm-receipt").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + "}"));
        return orderId;
    }

    private String notifyBody(String payNo, String tradeNo, long amount, int status, boolean correctSign) {
        String sign = sandboxPayChannel.sign(payNo, tradeNo, amount, status);
        if (!correctSign) {
            sign = "deadbeef" + sign.substring(8);
        }
        return "{\"payNo\":\"" + payNo + "\",\"channelTradeNo\":\"" + tradeNo + "\",\"amount\":" + amount
                + ",\"status\":" + status + ",\"sign\":\"" + sign + "\"}";
    }

    private static FreightQuoteResult freight(long amount) {
        FreightQuoteResult result = new FreightQuoteResult();
        result.setAmount(amount);
        result.setMatched(true);
        result.setFreeGap(0L);
        result.setTip("");
        return result;
    }

    private void stubSku(long sku, long goods, long price, int stock) {
        stubSku(sku, goods, price, stock, 1L);
    }

    private void stubSku(long sku, long goods, long price, int stock, Long merchantId) {
        SkuBrief s = new SkuBrief();
        s.setId(sku);
        s.setGoodsId(goods);
        s.setPrice(price);
        s.setStock(stock);
        s.setStatus(1);
        s.setSpecJson("[]");
        skuStubs.put(sku, s);

        GoodsBrief g = new GoodsBrief();
        g.setId(goods);
        g.setName("V12商品");
        g.setMainImage("/img/test.png");
        g.setStatus(1);
        g.setMerchantId(merchantId);
        goodsStubs.put(goods, g);
    }

    private AddressBrief addressOf(long id) {
        AddressBrief a = new AddressBrief();
        a.setId(id);
        a.setMemberId(memberId);
        a.setReceiverName("张三");
        a.setReceiverPhone("13800000000");
        a.setProvince("浙江省");
        a.setCity("杭州市");
        a.setDistrict("西湖区");
        a.setDetail("文一西路 1 号");
        return a;
    }

    private MemberBrief memberOf(long id) {
        MemberBrief m = new MemberBrief();
        m.setId(id);
        m.setNickname("会员" + id);
        return m;
    }
}
