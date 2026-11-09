package com.mall.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MarketingInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.RefundInnerClient;
import com.mall.common.feign.dto.MerchantBrief;
import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.CartBrief;
import com.mall.common.feign.dto.CouponBrief;
import com.mall.common.feign.dto.CouponRecordBrief;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6 T-01 交易主链回归（原单体 TradeFlowTest 按微服务边界改写）。
 *
 * <p>边界口径：订单域只对<b>自己的数据</b>做真实断言（{@code mall_order} / {@code mall_order_item} /
 * {@code mall_order_log} 状态机、金额、日志），跨域协作改为「断言发了什么 Feign 调用、参数对不对」；
 * 商品域库存的真实扣减/不超卖由 {@code GoodsStockInnerTest} 对着 {@code mall_sku} 断言。
 * 两者拼起来才是原来那 1 套全链路断言，但每层都由数据的归属方来验证。
 *
 * <p>下游（goods/member/marketing/refund）用 {@code @MockBean} 顶替，所以 {@code mvn test} 不依赖
 * 起了几个服务，可在 CI 裸跑；真连服务的那层由 fe-smoke 51 断言兜底。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderTradeFlowTest extends MallIntegrationTestBase {

    private static final int UNPAID = 10;
    private static final int PAID = 20;
    private static final int DELIVERED = 30;
    private static final int FINISHED = 40;
    private static final int CANCELLED = 50;

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
    private com.mall.service.OrderCloseService orderCloseService;

    private long memberA;
    private long memberB;
    private long addressId;
    private String tokenA;
    private String tokenB;
    private String adminToken;

    /** 本用例的「商品域快照」：改这里等于改下游返回的数据 */
    private final Map<Long, SkuBrief> skuStubs = new HashMap<>();
    private final Map<Long, GoodsBrief> goodsStubs = new HashMap<>();
    private long skuId;
    private long goodsId;

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("交易A");
        memberB = createMember("交易B");
        addressId = createAddress(memberA);
        tokenA = memberToken(memberA);
        tokenB = memberToken(memberB);
        adminToken = adminToken();

        goodsId = createGoods(createCategory(), "交易商品", 100L);
        skuId = createSku(goodsId, 100L, 10);
    }

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    @BeforeEach
    void stubDownstream() {
        skuStubs.clear();
        goodsStubs.clear();
        stubSku(skuId, goodsId, 100L, 10);

        when(goodsInnerClient.skuByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(skuStubs::get).filter(Objects::nonNull).toList();
        });
        when(goodsInnerClient.goodsByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(goodsStubs::get).filter(Objects::nonNull).toList();
        });
        when(goodsInnerClient.deductStock(anyMap())).thenReturn(true);

        when(memberInnerClient.address(anyLong())).thenAnswer(inv -> addressOf(inv.getArgument(0)));
        when(memberInnerClient.cartList(anyLong(), any())).thenReturn(List.of());
        when(memberInnerClient.member(anyLong())).thenAnswer(inv -> memberOf(inv.getArgument(0)));

        when(marketingInnerClient.availableRecords(anyLong())).thenReturn(List.of());
        when(marketingInnerClient.lockRecord(anyLong(), anyLong())).thenReturn(true);
        when(refundInnerClient.latestByOrderIds(anyList())).thenReturn(List.of());
        when(refundInnerClient.latestByOrderId(anyLong())).thenReturn(null);

        // V10：订单完成结算 / 店铺名展示
        when(merchantInnerClient.merchant(anyLong())).thenAnswer(inv -> {
            MerchantBrief brief = new MerchantBrief();
            brief.setId(inv.getArgument(0));
            brief.setName("测试店铺-" + inv.getArgument(0));
            brief.setStatus(1);
            return brief;
        });
        when(merchantInnerClient.settle(anyLong(), anyLong(), any(), anyLong())).thenReturn(true);
    }

    /**
     * V10：购物车跨商家 → 拆成两笔订单，各自独立金额与商家归属；
     * 跨店铺用券直接拒绝（券是订单级优惠，拆单后无法公平分摊）。
     */
    @Test
    void submitSplitsCartByMerchant() throws Exception {
        long merchantB = 88L;
        long goods2 = createGoods(createCategory(), "B店商品", 200L);
        long sku2 = createSku(goods2, 200L, 5);
        stubSku(skuId, goodsId, 100L, 10, 1L);
        stubSku(sku2, goods2, 200L, 5, merchantB);

        CartBrief c1 = new CartBrief();
        c1.setId(9001L);
        c1.setMemberId(memberA);
        c1.setSkuId(skuId);
        c1.setNum(1);
        CartBrief c2 = new CartBrief();
        c2.setId(9002L);
        c2.setMemberId(memberA);
        c2.setSkuId(sku2);
        c2.setNum(2);
        when(memberInnerClient.cartList(anyLong(), any())).thenReturn(List.of(c1, c2));

        // 确认页预览要能让前端提示「按店铺拆单」：items 带商家、根上带店铺数
        JsonNode confirm = ok(post("/shop/order/confirm").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        assertEquals(2, confirm.get("data").get("merchantCount").asInt(), "预览需给出店铺数供拆单提示");
        JsonNode confirmItems = confirm.get("data").get("items");
        assertEquals("1", confirmItems.get(0).get("merchantId").asText());
        assertEquals("测试店铺-1", confirmItems.get(0).get("merchantName").asText(), "预览需带店铺名");
        assertEquals(String.valueOf(merchantB), confirmItems.get(1).get("merchantId").asText());

        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + "}"));

        assertEquals(2, submit.get("data").get("orderCount").asInt(), "两个商家应拆成两笔订单");
        assertEquals(500L, submit.get("data").get("payAmount").asLong(), "实付合计 = 100×1 + 200×2");

        JsonNode orders = submit.get("data").get("orders");
        assertEquals("1", orders.get(0).get("merchantId").asText(), "第一笔归平台自营");
        assertEquals(100L, orders.get(0).get("payAmount").asLong());
        assertEquals(String.valueOf(merchantB), orders.get(1).get("merchantId").asText(), "第二笔归商家 B");
        assertEquals(400L, orders.get(1).get("payAmount").asLong());

        long orderIdB = Long.parseLong(orders.get(1).get("orderId").asText());
        assertEquals(merchantB, jdbc.queryForObject(
                "SELECT merchant_id FROM mall_order WHERE id = ?", Long.class, orderIdB), "订单落商家归属");
        assertEquals(merchantB, jdbc.queryForObject(
                "SELECT merchant_id FROM mall_order_item WHERE order_id = ?", Long.class, orderIdB), "明细落商家归属");
        assertEquals(1, countItems(orderIdB), "B 店订单只有自己的明细");

        // 跨店铺用券：明确拒绝，避免一笔券被多笔订单核销
        bizError(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"couponRecordId\":123}"), 400);
    }

    /** 全链路：提交→支付→幂等重放→后台发货→确认收货，状态机与日志逐环断言 */
    @Test
    void fullTradeFlow() throws Exception {
        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":2}"));
        long orderId = Long.parseLong(submit.get("data").get("orderId").asText());
        String orderNo = submit.get("data").get("orderNo").asText();
        assertEquals(200L, submit.get("data").get("payAmount").asLong(), "实付 = 单价 100 × 2");
        assertEquals(UNPAID, orderStatusOf(orderId), "下单后待付款");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> deductArgs = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).deductStock(deductArgs.capture());
        assertEquals(2, deductArgs.getValue().get(skuId), "必须按购买数量向商品域扣减");

        assertEquals(1, countItems(orderId), "写 1 条订单明细");
        assertEquals(2, jdbc.queryForObject(
                "SELECT quantity FROM mall_order_item WHERE order_id = ?", Integer.class, orderId));

        JsonNode pay = ok(post("/shop/pay/mock").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"));
        assertFalse(pay.get("data").get("replay").asBoolean(), "首次支付不是重放");
        assertEquals(PAID, orderStatusOf(orderId), "支付后待发货");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> salesArgs = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).incrementSkuSales(salesArgs.capture());
        assertEquals(2, salesArgs.getValue().get(skuId), "支付成功后按数量累加销量");
        verify(marketingInnerClient).useByOrder(orderId);

        JsonNode replay = ok(post("/shop/pay/mock").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"));
        assertTrue(replay.get("data").get("replay").asBoolean(), "重复支付按幂等重放返回");
        verify(goodsInnerClient, times(1)).incrementSkuSales(anyMap());
        verify(marketingInnerClient, times(1)).useByOrder(anyLong());

        ok(post("/mall_order/deliver").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + orderId + ",\"expressCompany\":\"顺丰\",\"expressNo\":\"SF123\"}"));
        assertEquals(DELIVERED, orderStatusOf(orderId), "发货后已发货");

        ok(post("/shop/order/confirm-receipt").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"));
        assertEquals(FINISHED, orderStatusOf(orderId), "确认收货后已完成");

        Integer logs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_order_log WHERE order_id = ?", Integer.class, orderId);
        assertTrue(logs != null && logs >= 4, "提交/支付/发货/收货每步都要留痕，实际 " + logs);
    }

    /** 下单前快照判定库存不足：400 且不产生订单、不发起扣减 */
    @Test
    void oversellRejectedBeforeDeducting() throws Exception {
        stubSku(skuId, goodsId, 100L, 1);
        int ordersBefore = countOrders(memberA);

        bizError(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":2}"), 400);

        assertEquals(ordersBefore, countOrders(memberA), "失败不得落订单");
        verify(goodsInnerClient, never()).deductStock(anyMap());
    }

    /** 快照够、真扣时已售罄：400 且订单/明细/日志一条不留（事务回滚） */
    @Test
    void deductRaceLossRollsBackOrder() throws Exception {
        when(goodsInnerClient.deductStock(anyMap())).thenReturn(false);
        int ordersBefore = countOrders(memberA);
        int itemsBefore = countItemsOf(memberA);

        bizError(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":1}"), 400);

        assertEquals(ordersBefore, countOrders(memberA), "扣减失败必须整体回滚");
        assertEquals(itemsBefore, countItemsOf(memberA), "明细同样不得残留");
    }

    /** 取消：10→50，回补库存经 Feign 且释放优惠券；重复取消被闸门拒绝 */
    @Test
    void cancelRestoresStockAndReleasesCoupon() throws Exception {
        long orderId = submitOrder(3);

        ok(post("/shop/order/cancel").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"));
        assertEquals(CANCELLED, orderStatusOf(orderId), "取消后状态 50");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> restoreArgs = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient).restoreStock(restoreArgs.capture());
        assertEquals(3, restoreArgs.getValue().get(skuId), "回补数量=购买数量");
        verify(marketingInnerClient).releaseByOrder(orderId);

        bizError(post("/shop/order/cancel").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"), 400);
        verify(goodsInnerClient, times(1)).restoreStock(anyMap());
    }

    /** 非法状态迁移一律 400：待付款不能确认收货，已支付不能取消 */
    @Test
    void illegalTransitionRejected() throws Exception {
        long orderId = submitOrder(1);
        String orderNo = jdbc.queryForObject("SELECT order_no FROM mall_order WHERE id = ?", String.class, orderId);

        bizError(post("/shop/order/confirm-receipt").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"), 400);

        ok(post("/shop/pay/mock").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"));
        bizError(post("/shop/order/cancel").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"), 400);
        assertEquals(PAID, orderStatusOf(orderId), "非法取消不得改状态");
    }

    /** 越权：他人订单详情/取消 403 */
    @Test
    void crossMemberForbidden() throws Exception {
        long orderId = submitOrder(1);

        bizError(get("/shop/order/" + orderId).header("Authorization", bearer(tokenB)), 403);
        bizError(post("/shop/order/cancel").header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"), 403);

        ok(post("/shop/order/cancel").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"));
    }

    /** 匿名写接口 401（白名单只放浏览与登录，不放下单/支付） */
    @Test
    void anonymousWriteRejected() throws Exception {
        mvc.perform(post("/shop/order/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/shop/pay/mock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"X\"}")).andExpect(status().isUnauthorized());
    }

    /** 购物车下单：勾选 2 行 → 订单 2 条明细，下单后清空对应购物车项 */
    @Test
    void cartOrderCreatesItemsAndClearsCart() throws Exception {
        long sku2 = createSku(goodsId, 200L, 5);
        stubSku(sku2, goodsId, 200L, 5);
        when(memberInnerClient.cartList(anyLong(), any())).thenReturn(List.of(cart(9001L, skuId, 1), cart(9002L, sku2, 2)));

        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"));
        long orderId = Long.parseLong(submit.get("data").get("orderId").asText());

        assertEquals(2, countItems(orderId), "购物车两行下成两条明细");
        assertEquals(500L, submit.get("data").get("payAmount").asLong(), "100×1 + 200×2 = 500");
        verify(goodsInnerClient, times(2)).deductStock(anyMap());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> cartIds = ArgumentCaptor.forClass(List.class);
        verify(memberInnerClient).clearCart(anyLong(), cartIds.capture());
        assertEquals(List.of(9001L, 9002L), new ArrayList<>(cartIds.getValue()), "只清已下单的购物车行");
    }

    /** 优惠券：满足门槛可锁定并抵扣；锁定失败（并发被人抢）→ 400 且订单回滚 */
    @Test
    void couponLockAndConflict() throws Exception {
        CouponRecordBrief record = new CouponRecordBrief();
        record.setId(7001L);
        record.setCouponId(8001L);
        record.setMemberId(memberA);
        record.setStatus(1);
        CouponBrief coupon = new CouponBrief();
        coupon.setId(8001L);
        coupon.setName("满 100 减 30");
        coupon.setType(1);
        coupon.setAmount(30L);
        coupon.setThresholdAmount(100L);
        coupon.setStatus(1);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(1));
        when(marketingInnerClient.availableRecords(memberA)).thenReturn(List.of(record));
        when(marketingInnerClient.couponByIds(anyList())).thenReturn(List.of(coupon));
        when(marketingInnerClient.lockRecord(7001L, 0L)).thenReturn(true);

        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":2,\"couponRecordId\":7001}"));
        assertEquals(170L, submit.get("data").get("payAmount").asLong(), "200 - 30 = 170");

        when(marketingInnerClient.lockRecord(anyLong(), anyLong())).thenReturn(false);
        bizError(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":2,\"couponRecordId\":7001}"), 400);
    }

    /** 超时关单：待付款且超时 → 60 并回补；未超时不动；重复执行幂等（不二次回补） */
    @Test
    void closeExpiredOrdersIsIdempotent() throws Exception {
        long expired = submitOrder(2);
        long fresh = submitOrder(1);
        jdbc.update("UPDATE mall_order SET create_time = NOW() - INTERVAL 60 MINUTE WHERE id = ?", expired);

        int closed = orderCloseService.closeExpiredOrders(30, 200);
        assertTrue(closed >= 1, "至少关闭 1 单，实际 " + closed);
        assertEquals(60, orderStatusOf(expired), "超时单关成 60 已关闭");
        assertEquals(UNPAID, orderStatusOf(fresh), "未超时的单不许动");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Integer>> restoreArgs = ArgumentCaptor.forClass(Map.class);
        verify(goodsInnerClient, times(1)).restoreStock(restoreArgs.capture());
        assertEquals(2, restoreArgs.getValue().get(skuId));

        assertEquals(0, orderCloseService.closeExpiredOrders(30, 200), "二次执行无候选，天然幂等");
        verify(goodsInnerClient, times(1)).restoreStock(anyMap());
    }

    /** 后台关闭已付款未发货订单：20→60 并回补 */
    @Test
    void adminClosePaidOrderRestocks() throws Exception {
        long orderId = submitOrder(2);
        String orderNo = jdbc.queryForObject("SELECT order_no FROM mall_order WHERE id = ?", String.class, orderId);
        ok(post("/shop/pay/mock").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"));

        ok(post("/mall_order/close").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"));
        assertEquals(60, orderStatusOf(orderId), "后台关闭后 60");
        verify(goodsInnerClient).restoreStock(anyMap());

        // 幂等：已关闭的单再关一次 400，库存不二次回补
        bizError(post("/mall_order/close").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"), 400);
        verify(goodsInnerClient, times(1)).restoreStock(anyMap());
    }

    // ===== 夹具小工具 =====

    private long submitOrder(int num) throws Exception {
        JsonNode submit = ok(post("/shop/order/submit").header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":" + addressId + ",\"skuId\":" + skuId + ",\"num\":" + num + "}"));
        return Long.parseLong(submit.get("data").get("orderId").asText());
    }

    private void stubSku(long sku, long goods, long price, int stock) {
        stubSku(sku, goods, price, stock, 1L);
    }

    private void stubSku(long sku, long goods, long price, int stock, long merchantId) {
        SkuBrief brief = new SkuBrief();
        brief.setId(sku);
        brief.setGoodsId(goods);
        brief.setSkuCode("IT-SKU-" + sku);
        brief.setSpecJson("[]");
        brief.setPrice(price);
        brief.setStock(stock);
        brief.setStatus(1);
        skuStubs.put(sku, brief);

        GoodsBrief goodsBrief = new GoodsBrief();
        goodsBrief.setId(goods);
        goodsBrief.setName("交易商品-" + goods);
        goodsBrief.setMainImage("/img/test.png");
        goodsBrief.setPriceMin(price);
        goodsBrief.setPriceMax(price);
        goodsBrief.setStatus(1);
        goodsBrief.setMerchantId(merchantId);
        goodsStubs.put(goods, goodsBrief);
    }

    private AddressBrief addressOf(Long id) {
        AddressBrief brief = new AddressBrief();
        brief.setId(id);
        brief.setMemberId(memberA);
        brief.setReceiverName("测试收件人");
        brief.setReceiverPhone("19900000000");
        brief.setProvince("浙江省");
        brief.setCity("杭州市");
        brief.setDistrict("西湖区");
        brief.setDetail("测试路 1 号");
        brief.setIsDefault(1);
        return brief;
    }

    private MemberBrief memberOf(Long id) {
        MemberBrief brief = new MemberBrief();
        brief.setId(id);
        brief.setNickname("会员" + id);
        return brief;
    }

    private CartBrief cart(long cartId, long sku, int num) {
        CartBrief brief = new CartBrief();
        brief.setId(cartId);
        brief.setMemberId(memberA);
        brief.setSkuId(sku);
        brief.setNum(num);
        brief.setChecked(1);
        return brief;
    }

    private int countOrders(long memberId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mall_order WHERE member_id = ?", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    private int countItemsOf(long memberId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mall_order_item WHERE order_id IN"
                + " (SELECT id FROM mall_order WHERE member_id = ?)", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    private int countItems(long orderId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mall_order_item WHERE order_id = ?", Integer.class, orderId);
        return n == null ? 0 : n;
    }
}
