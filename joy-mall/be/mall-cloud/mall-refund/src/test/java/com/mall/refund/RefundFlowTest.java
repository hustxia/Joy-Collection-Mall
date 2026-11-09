package com.mall.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.OrderLogAppendDTO;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V6 T-01 售后状态机回归（原单体 RefundFlowTest 改写）。
 *
 * <p>售后单只存在售后域 {@code mall_refund}，所以状态机（1 待审核 → 2 已同意 → 4 已退款，
 * 3 已拒绝 / 5 已关闭）在本地断言；库存回补与销量回减属商品域，改为断言 Feign 调用与参数符号；
 * 「订单是否可申请、是否本人订单」依赖订单域快照，用 mock 返回不同订单状态覆盖分支。
 *
 * <p>幂等口径保持原样：条件 UPDATE 是闸门，重复退款必须 400 且<b>不二次回补库存</b>。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundFlowTest extends MallIntegrationTestBase {

    /** V10：退款完成会回调商家冲减 */
    @MockBean
    private MerchantInnerClient merchantInnerClient;

    @MockBean
    private OrderInnerClient orderInnerClient;
    @MockBean
    private MemberInnerClient memberInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;

    private long memberA;
    private long memberB;
    private long goodsId;
    private long skuId;
    private String tokenA;
    private String tokenB;
    private String adminToken;

    /** 本用例的「订单域快照」：key=orderId */
    private final Map<Long, OrderBrief> orderStubs = new HashMap<>();

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("售后A");
        memberB = createMember("售后B");
        tokenA = memberToken(memberA);
        tokenB = memberToken(memberB);
        adminToken = adminToken();
        goodsId = createGoods(createCategory(), "售后商品", 100L);
        skuId = createSku(goodsId, 100L, 10);
    }

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    @BeforeEach
    void stubDownstream() {
        orderStubs.clear();
        when(orderInnerClient.order(anyLong())).thenAnswer(inv -> orderStubs.get(inv.<Long>getArgument(0)));
        when(orderInnerClient.orderItems(anyLong())).thenAnswer(inv -> List.of(itemOf(inv.getArgument(0), 2)));
        when(memberInnerClient.member(anyLong())).thenAnswer(inv -> memberOf(inv.getArgument(0)));
        when(memberInnerClient.membersByIds(anyList())).thenReturn(List.of());
    }

    /** 退货退款：审核通过→退款→回补库存+回减销量；重复退款 400 且不二次回补 */
    @Test
    void returnRefundRestocksAndIsIdempotent() throws Exception {
        long orderId = createDeliveredOrder(2, 200L);

        long refundId = apply(orderId, 2, "质量不好", tokenA);
        assertEquals(1, refundStatusOf(refundId), "申请后待审核");

        bizError(post("/shop/refund/apply").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"type\":1,\"reason\":\"重复申请\"}"), 400);

        ok(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"agree\":true,\"auditRemark\":\"凭证齐全\"}"));
        assertEquals(2, refundStatusOf(refundId), "审核通过 → 已同意");

        bizError(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"agree\":false,\"auditRemark\":\"再审\"}"), 400);

        ok(post("/mall_refund/refund").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"));
        assertEquals(4, refundStatusOf(refundId), "退款后已退款");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> restore = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).restoreStock(restore.capture());
        assertEquals(2, restore.getValue().get(skuId), "退货退款按购买数量回补库存");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> sales = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).incrementSkuSales(sales.capture());
        assertEquals(-2, sales.getValue().get(skuId), "销量按负数回减");

        // 幂等：重复退款 400，库存不二次回补
        bizError(post("/mall_refund/refund").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"), 400);
        verify(goodsInnerClient, times(1)).restoreStock(anyMap());

        // 已退款仍在「进行中」口径，不允许再申请
        bizError(post("/shop/refund/apply").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"type\":1,\"reason\":\"再申请\"}"), 400);
    }

    /** 仅退款：不动库存，也不回减销量 */
    @Test
    void onlyRefundKeepsStock() throws Exception {
        long orderId = createDeliveredOrder(1, 100L);
        long refundId = apply(orderId, 1, "不想要了", tokenA);

        ok(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + ",\"agree\":true}"));
        ok(post("/mall_refund/refund").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"));

        assertEquals(4, refundStatusOf(refundId));
        verify(goodsInnerClient, never()).restoreStock(anyMap());
        verify(goodsInnerClient, never()).incrementSkuSales(anyMap());
    }

    /** 拒绝必须带意见；拒绝（3 终态）后可重新申请 */
    @Test
    void rejectRequiresRemarkThenReapply() throws Exception {
        long orderId = createDeliveredOrder(1, 100L);
        long refundId = apply(orderId, 1, "要求拒绝我", tokenA);

        bizError(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"agree\":false}"), 400);

        ok(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"agree\":false,\"auditRemark\":\"凭证不足\"}"));
        assertEquals(3, refundStatusOf(refundId), "拒绝后已拒绝");

        long again = apply(orderId, 1, "补充凭证后再申请", tokenA);
        assertEquals(1, refundStatusOf(again), "终态后可重新申请");
    }

    /** 越权 403；本人撤销 1→5；撤销后可重新申请 */
    @Test
    void closeAndCrossMember() throws Exception {
        long orderId = createDeliveredOrder(1, 100L);

        bizError(post("/shop/refund/apply").header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"type\":1,\"reason\":\"偷别人订单\"}"), 403);

        long refundId = apply(orderId, 1, "先撤销试试", tokenA);
        bizError(post("/shop/refund/close").header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"), 403);

        ok(post("/shop/refund/close").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"));
        assertEquals(5, refundStatusOf(refundId), "撤销后已关闭");

        long again = apply(orderId, 1, "重新申请", tokenA);
        assertEquals(1, refundStatusOf(again));
    }

    /** 订单状态不支持（待付款 10）→ 400；订单不属于本人 → 403 */
    @Test
    void applyRejectedByOrderState() throws Exception {
        long unpaid = createOrder(memberA, 10, 100L);
        bizError(post("/shop/refund/apply").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + unpaid + ",\"type\":1,\"reason\":\"未付款也要退\"}"), 400);

        long othersOrder = createDeliveredOrder(1, 100L);
        bizError(post("/shop/refund/apply").header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + othersOrder + ",\"type\":1,\"reason\":\"越权\"}"), 403);
    }

    /** 每次状态流转都要往订单域追加流转日志（售后域不自己存订单日志） */
    @Test
    void everyTransitionAppendsOrderLog() throws Exception {
        long orderId = createDeliveredOrder(1, 100L);
        long refundId = apply(orderId, 1, "留痕测试", tokenA);
        ok(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"agree\":true,\"auditRemark\":\"同意\"}"));
        ok(post("/mall_refund/refund").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"));

        ArgumentCaptor<OrderLogAppendDTO> logs = ArgumentCaptor.forClass(OrderLogAppendDTO.class);
        verify(orderInnerClient, times(3)).appendLog(logs.capture());
        String joined = logs.getAllValues().stream().map(OrderLogAppendDTO::getContent)
                .reduce("", (a, b) -> a + "|" + b);
        assertTrue(joined.contains("提交售后申请"), "申请要留痕：" + joined);
        assertTrue(joined.contains("售后审核通过"), "审核要留痕：" + joined);
        assertTrue(joined.contains("售后退款完成"), "退款要留痕：" + joined);
    }

    // ===== 夹具 =====

    private long createDeliveredOrder(int qty, long payAmount) {
        return createOrder(memberA, 30, payAmount, qty);
    }

    private long createOrder(long memberId, int status, long payAmount) {
        return createOrder(memberId, status, payAmount, 1);
    }

    private long createOrder(long memberId, int status, long payAmount, int qty) {
        String orderNo = TEST_MARK + "O-" + newPhone();
        jdbc.update("INSERT INTO mall_order (order_no, member_id, total_amount, freight_amount, discount_amount,"
                        + " pay_amount, status, pay_status, pay_type, pay_time, receiver_name, receiver_phone,"
                        + " receiver_address, deliver_time)"
                        + " VALUES (?,?,?,0,0,?,?,?,9,NOW(),?,?,?,NOW())",
                orderNo, memberId, payAmount, payAmount, status, status >= 20 ? 1 : 0,
                "售后收件人", "19900000000", "浙江省杭州市西湖区测试路 1 号");
        Long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);

        jdbc.update("INSERT INTO mall_order_item (order_id, goods_id, sku_id, goods_name, price, quantity, total_amount)"
                + " VALUES (?,?,?,?,?,?,?)", orderId, goodsId, skuId, "售后商品", 100L, qty, 100L * qty);

        OrderBrief brief = new OrderBrief();
        brief.setId(orderId);
        brief.setOrderNo(orderNo);
        brief.setMemberId(memberId);
        brief.setStatus(status);
        brief.setPayAmount(payAmount);
        brief.setPayOrderNo("MOCK-TEST");
        orderStubs.put(orderId, brief);
        return orderId;
    }

    private long apply(long orderId, int type, String reason, String token) throws Exception {
        JsonNode data = ok(post("/shop/refund/apply").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"type\":" + type + ",\"reason\":\"" + reason + "\"}"));
        return Long.parseLong(data.get("data").get("refundId").asText());
    }

    private OrderItemBrief itemOf(Long orderId, int qty) {
        OrderItemBrief item = new OrderItemBrief();
        item.setOrderId(orderId);
        item.setGoodsId(goodsId);
        item.setSkuId(skuId);
        item.setGoodsName("售后商品");
        item.setPrice(100L);
        item.setQuantity(qty);
        item.setTotalAmount(100L * qty);
        return item;
    }

    private MemberBrief memberOf(Long id) {
        MemberBrief brief = new MemberBrief();
        brief.setId(id);
        brief.setNickname("会员" + id);
        return brief;
    }
}
