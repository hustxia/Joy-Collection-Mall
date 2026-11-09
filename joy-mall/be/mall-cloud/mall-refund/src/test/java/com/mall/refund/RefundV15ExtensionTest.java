package com.mall.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
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
 * V15 售后扩展（REQ-P1-06）集成测试：换货全流程 / 部分退款累计封顶 / 平台介入与仲裁。
 *
 * <p>延续 RefundFlowTest 口径：售后单状态机在本地断言；库存回补/扣减属商品域，
 * 断言 Feign 调用与参数（换出 SKU 正数回补、换入 SKU 正数扣减）；订单快照 mock。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundV15ExtensionTest extends MallIntegrationTestBase {

    @MockBean
    private MerchantInnerClient merchantInnerClient;

    @MockBean
    private OrderInnerClient orderInnerClient;
    @MockBean
    private MemberInnerClient memberInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;

    private long memberA;
    private long goodsId;
    private long skuId;
    private long exchangeSkuId;
    private String tokenA;
    private String adminToken;

    private final Map<Long, OrderBrief> orderStubs = new HashMap<>();
    private final Map<Long, List<OrderItemBrief>> itemStubs = new HashMap<>();
    private long itemIdSeq = 900001L;

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("V15售后A");
        tokenA = memberToken(memberA);
        adminToken = adminToken();
        goodsId = createGoods(createCategory(), "V15换货商品", 100L);
        skuId = createSku(goodsId, 100L, 10);
        exchangeSkuId = createSku(goodsId, 120L, 10);
        when(memberInnerClient.member(anyLong())).thenAnswer(inv -> {
            MemberBrief brief = new MemberBrief();
            brief.setId(inv.getArgument(0, Long.class));
            brief.setNickname("会员" + inv.getArgument(0, Long.class));
            return brief;
        });
        when(memberInnerClient.membersByIds(anyList())).thenReturn(List.of());
        when(goodsInnerClient.deductStock(anyMap())).thenReturn(true);
    }

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    @BeforeEach
    void stubDownstream() {
        orderStubs.clear();
        itemStubs.clear();
        when(orderInnerClient.order(anyLong())).thenAnswer(inv -> orderStubs.get(inv.<Long>getArgument(0)));
        when(orderInnerClient.orderItems(anyLong()))
                .thenAnswer(inv -> itemStubs.getOrDefault(inv.<Long>getArgument(0), List.of()));
        org.mockito.Mockito.reset(merchantInnerClient, goodsInnerClient);
        when(goodsInnerClient.deductStock(anyMap())).thenReturn(true);
        when(goodsInnerClient.skuByIds(anyList()))
                .thenAnswer(inv -> List.of(new com.mall.common.feign.dto.SkuBrief()));
    }

    /** 换货全流程：申请→审核→买家寄回→收货确认（回补换出）→发出换货（扣减换入），重复操作幂等 */
    @Test
    void exchangeFullFlowAndStockDeltas() throws Exception {
        long orderId = createDeliveredOrder(2, 200L);

        // 申请：换入 SKU 必填
        bizError(post("/shop/refund/apply/exchange").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"reason\":\"尺码不合适\"}"), 400);

        long refundId = applyExchange(orderId, 2);
        assertEquals(1, refundStatusOf(refundId), "申请后待审核");

        // 审核通过 → 待寄回
        ok(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + ",\"agree\":true}"));
        assertEquals(2, refundStatusOf(refundId), "审核通过 → 已同意（待买家寄回）");

        // 买家寄回：2 → 6，可重复寄回被闸门拦下
        ok(post("/shop/refund/ship").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"));
        assertEquals(6, refundStatusOf(refundId), "寄回后买家已寄回");
        bizError(post("/shop/refund/ship").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"), 400);

        // 收货确认：6 → 7，回补换出 SKU（+2）
        ok(post("/mall_refund/confirm-receipt").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"));
        assertEquals(7, refundStatusOf(refundId), "收货确认后商家已收货");
        bizError(post("/mall_refund/confirm-receipt").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"), 400);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> restore = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).restoreStock(restore.capture());
        assertEquals(2, restore.getValue().get(skuId), "收货确认才回补换出 SKU 库存（+2）");

        // 发出换货：7 → 8（终态），扣减换入 SKU（-2）；同 SKU 换货净变化为 0
        ok(post("/mall_refund/send-exchange").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"));
        assertEquals(8, refundStatusOf(refundId), "发出换货后已换货（终态）");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> deduct = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).deductStock(deduct.capture());
        assertEquals(2, deduct.getValue().get(exchangeSkuId), "发出换货时扣减换入 SKU 库存（-2）");

        // 幂等：重复发出 400，不二次扣减
        bizError(post("/mall_refund/send-exchange").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + refundId + "}"), 400);
        verify(goodsInnerClient, times(1)).deductStock(anyMap());
    }

    /** 部分退款：按订单项选金额，一单多次；累计超实付 400；不动库存 */
    @Test
    void partialRefundCumulativeCapAndNoStock() throws Exception {
        long orderId = createDeliveredOrder(2, 200L);
        long itemId = firstItemId(orderId);

        // 超订单项实付 400（订单项 2 件 × 100 分 = 200 分）
        bizError(post("/shop/refund/apply/part").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"orderItemId\":" + itemId + ",\"amount\":500,\"reason\":\"多了\"}"), 400);

        // 第一笔 50 分，审核退款走主流程
        long r1 = applyPartial(orderId, itemId, 50);
        assertEquals(1, refundStatusOf(r1));
        ok(post("/mall_refund/audit").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + r1 + ",\"agree\":true}"));
        ok(post("/mall_refund/refund").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + r1 + "}"));
        assertEquals(4, refundStatusOf(r1), "部分退款走 1→2→4 主流程");
        verify(goodsInnerClient, never()).restoreStock(anyMap());
        verify(goodsInnerClient, never()).deductStock(anyMap());

        // 第二笔 100 分（可与已退的部分退款并存）
        long r2 = applyPartial(orderId, itemId, 100);
        assertEquals(1, refundStatusOf(r2));

        // 进行中的也算累计：50 + 100 + 本次 60 = 210 分 > 200 分 → 400
        bizError(post("/shop/refund/apply/part").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"orderItemId\":" + itemId + ",\"amount\":60,\"reason\":\"超了\"}"), 400);

        // 恰好补满剩余 50 分 → 成功（累计 200 分 = 实付）
        long r3 = applyPartial(orderId, itemId, 50);
        assertEquals(1, refundStatusOf(r3));
    }

    /** 48 小时超时：未超时不可介入；改超时后进入仲裁队列；强制退款走主流程终态 */
    @Test
    void arbitrationAfterTimeoutAndForceRefund() throws Exception {
        long orderId = createDeliveredOrder(1, 100L);
        long refundId = applyFull(orderId, 1, "商家一直不处理");

        // 未超 48h：申请介入 400，仲裁队列不含该单
        bizError(post("/shop/refund/intervene").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"), 400);
        assertEquals(0, queueCount(refundId), "未超时的工单不进仲裁队列");

        // 惰性判定：把申请时间拨回 49 小时前 → 自动入队（无需定时任务）
        jdbc.update("UPDATE mall_refund SET create_time = DATE_SUB(NOW(), INTERVAL 49 HOUR) WHERE id = ?", refundId);
        assertEquals(1, queueCount(refundId), "超 48h 未处理的工单进入仲裁队列");

        // 买家申请介入 → 仲裁中；重复介入幂等 400
        ok(post("/shop/refund/intervene").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"));
        assertEquals(1, platformStatusOf(refundId), "介入后仲裁中");
        bizError(post("/shop/refund/intervene").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":" + refundId + "}"), 400);

        // 强制退款必须带意见
        bizError(post("/mall_refund/arbitration/verdict").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"forceRefund\":true}"), 400);

        // 裁决强制退款 → 复用退款主流程到终态 4，platform_status=2
        ok(post("/mall_refund/arbitration/verdict").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"forceRefund\":true,\"remark\":\"商家超时未处理，强制退款\"}"));
        assertEquals(4, refundStatusOf(refundId), "仲裁强制退款走退款主流程到终态");
        assertEquals(2, platformStatusOf(refundId), "platform_status=已强制退款");

        // 重复裁决 400
        bizError(post("/mall_refund/arbitration/verdict").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"forceRefund\":false,\"remark\":\"再裁\"}"), 400);
    }

    /** 仲裁驳回：platform_status=3，工单保留待审核可由商家继续处理 */
    @Test
    void arbitrationRejectKeepsPending() throws Exception {
        long orderId = createDeliveredOrder(1, 100L);
        long refundId = applyFull(orderId, 2, "要求退货退款");
        jdbc.update("UPDATE mall_refund SET create_time = DATE_SUB(NOW(), INTERVAL 50 HOUR) WHERE id = ?", refundId);

        ok(post("/mall_refund/arbitration/verdict").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + refundId + ",\"forceRefund\":false,\"remark\":\"证据不足，驳回\"}"));
        assertEquals(3, platformStatusOf(refundId), "驳回后 platform_status=3");
        assertEquals(1, refundStatusOf(refundId), "驳回后工单保持待审核，商家可继续处理");
    }

    // ===== 夹具 =====

    private long createDeliveredOrder(int qty, long payAmount) {
        String orderNo = TEST_MARK + "O-" + newPhone();
        jdbc.update("INSERT INTO mall_order (order_no, member_id, total_amount, freight_amount, discount_amount,"
                        + " pay_amount, status, pay_status, pay_type, pay_time, receiver_name, receiver_phone,"
                        + " receiver_address, deliver_time)"
                        + " VALUES (?,?,?,0,0,?,?,?,9,NOW(),?,?,?,NOW())",
                orderNo, memberA, payAmount, payAmount, 30, 1,
                "V15收件人", "19900000000", "浙江省杭州市西湖区测试路 1 号");
        Long orderId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);

        long itemId = itemIdSeq++;
        jdbc.update("INSERT INTO mall_order_item (order_id, goods_id, sku_id, goods_name, price, quantity, total_amount)"
                        + " VALUES (?,?,?,?,?,?,?)", orderId, goodsId, skuId, "V15换货商品", 100L, qty, 100L * qty);
        Long dbItemId = jdbc.queryForObject(
                "SELECT id FROM mall_order_item WHERE order_id = ? LIMIT 1", Long.class, orderId);

        OrderBrief brief = new OrderBrief();
        brief.setId(orderId);
        brief.setOrderNo(orderNo);
        brief.setMemberId(memberA);
        brief.setStatus(30);
        brief.setPayAmount(payAmount);
        brief.setPayOrderNo("MOCK-TEST");
        orderStubs.put(orderId, brief);

        OrderItemBrief item = new OrderItemBrief();
        item.setId(dbItemId == null ? itemId : dbItemId);
        item.setOrderId(orderId);
        item.setGoodsId(goodsId);
        item.setSkuId(skuId);
        item.setGoodsName("V15换货商品");
        item.setPrice(100L);
        item.setQuantity(qty);
        item.setTotalAmount(100L * qty);
        itemStubs.put(orderId, List.of(item));
        return orderId;
    }

    private long firstItemId(long orderId) {
        return itemStubs.get(orderId).get(0).getId();
    }

    private long applyExchange(long orderId, int qty) throws Exception {
        long itemId = firstItemId(orderId);
        JsonNode data = ok(post("/shop/refund/apply/exchange").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"orderItemId\":" + itemId
                        + ",\"exchangeSkuId\":" + exchangeSkuId + ",\"quantity\":" + qty
                        + ",\"reason\":\"尺码不合适\"}"));
        return Long.parseLong(data.get("data").get("refundId").asText());
    }

    private long applyPartial(long orderId, long itemId, long amount) throws Exception {
        JsonNode data = ok(post("/shop/refund/apply/part").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"orderItemId\":" + itemId
                        + ",\"amount\":" + amount + ",\"reason\":\"部分商品有问题\"}"));
        return Long.parseLong(data.get("data").get("refundId").asText());
    }

    private long applyFull(long orderId, int type, String reason) throws Exception {
        JsonNode data = ok(post("/shop/refund/apply").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"type\":" + type + ",\"reason\":\"" + reason + "\"}"));
        return Long.parseLong(data.get("data").get("refundId").asText());
    }

    /** 仲裁队列中是否包含该售后单（1 含 / 0 不含） */
    private int queueCount(long refundId) throws Exception {
        JsonNode root = ok(post("/mall_refund/arbitration/page").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pageNum\":1,\"pageSize\":50}"));
        JsonNode records = root.get("data").get("records");
        for (JsonNode rec : records) {
            if (rec.get("id").asLong() == refundId) {
                return 1;
            }
        }
        return 0;
    }

    private int platformStatusOf(long refundId) {
        Integer v = jdbc.queryForObject("SELECT platform_status FROM mall_refund WHERE id = ?",
                Integer.class, refundId);
        return v == null ? -1 : v;
    }
}
