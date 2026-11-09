package com.mall.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V10 商家域集成测试：入驻状态机 → 结算幂等 → 抽成口径 → 退款冲减 → 提现余额 → 结算单归集。
 *
 * <p>资金口径是本版本的风险中心，断言都落在「分」上：
 * 平台抽成 = 实付 × 费率(‰) ÷ 1000，商家应收 = 实付 − 抽成，两者相加恒等于实付。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MerchantFlowTest extends MallIntegrationTestBase {

    private final ObjectMapper om = new ObjectMapper();
    /** 本类创建的商家，@AfterAll 清理 */
    private final Set<Long> merchantIds = new LinkedHashSet<>();
    /** 订单 ID 只做幂等键，不需真实订单行 */
    private final AtomicLong orderSeq = new AtomicLong(System.currentTimeMillis());

    @AfterAll
    void tearDown() {
        for (Long id : merchantIds) {
            jdbc.update("DELETE FROM mall_merchant_refund_adjust WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM mall_merchant_bill_detail WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM mall_merchant_settlement WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM mall_merchant_withdrawal WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM mall_merchant_audit_log WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM mall_merchant WHERE id = ?", id);
        }
        merchantIds.clear();
        tearDownFixtures();
    }

    @Test
    void applyAuditStateMachine() throws Exception {
        String license = "LIC-" + System.currentTimeMillis();
        long id = applyMerchant("测试旗舰店A", license, 100);
        assertEquals(0, merchantStatus(id), "新申请应为待审");

        // 同一执照重复申请（未被驳回）直接拒绝
        bizError(mvc.perform(post("/mall_merchant/apply")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(applyBody("测试旗舰店A-重复", license, 100)))), 400);

        // 驳回必须给原因
        bizError(mvc.perform(post("/mall_merchant/audit")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", String.valueOf(id), "pass", false)))), 400);

        ok(mvc.perform(post("/mall_merchant/audit")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", String.valueOf(id), "pass", true)))));
        assertEquals(1, merchantStatus(id), "审核通过应启用");

        // 已审核过的再审核 → 400
        bizError(mvc.perform(post("/mall_merchant/audit")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", String.valueOf(id), "pass", true)))), 400);

        // 停用 → 2，启用 → 1
        ok(mvc.perform(post("/mall_merchant/status")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", String.valueOf(id), "enable", false)))));
        assertEquals(2, merchantStatus(id));
        ok(mvc.perform(post("/mall_merchant/status")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", String.valueOf(id), "enable", true)))));
        assertEquals(1, merchantStatus(id));

        // 详情带审核流水（提交 + 通过 + 停用 + 启用 = 4 条）
        JsonNode detail = ok(mvc.perform(get("/mall_merchant/" + id)
                .header("Authorization", bearer(adminToken()))));
        assertEquals(4, detail.get("data").get("logs").size(), "审核流水条数应为 4");
    }

    @Test
    void settleIsIdempotentAndRateExact() throws Exception {
        long merchantId = applyAndEnable("测试结算店", 100); // 10%
        long orderId = orderSeq.incrementAndGet();
        long pay = 10_000L;

        assertTrue(innerSettle(orderId, merchantId, pay), "首次结算应入账");
        // 抽成 = 10000 × 100‰ = 1000；商家应收 = 9000
        assertEquals(1_000L, queryLong("SELECT platform_fee FROM mall_merchant_bill_detail"
                + " WHERE order_id = ? AND merchant_id = ? AND deleted = 0", orderId, merchantId));
        assertEquals(9_000L, queryLong("SELECT goods_amount FROM mall_merchant_bill_detail"
                + " WHERE order_id = ? AND merchant_id = ? AND deleted = 0", orderId, merchantId));
        assertEquals(9_000L, merchantBalance(merchantId));

        // 重复事件幂等：不入账、不加钱、不新增明细
        assertFalse(innerSettle(orderId, merchantId, pay), "重复结算应被幂等跳过");
        assertEquals(9_000L, merchantBalance(merchantId), "重复结算不得重复加余额");
        assertEquals(1L, queryLong("SELECT COUNT(*) FROM mall_merchant_bill_detail"
                + " WHERE order_id = ? AND merchant_id = ?", orderId, merchantId));
    }

    @Test
    void refundAdjustProportionalAndIdempotent() throws Exception {
        long merchantId = applyAndEnable("测试退款店", 100); // 10%
        long orderId = orderSeq.incrementAndGet();
        long refundId = orderSeq.incrementAndGet();
        innerSettle(orderId, merchantId, 10_000L); // 商家 9000，平台 1000

        // 退 3000 → 商家冲减 3000×9000/10000 = 2700，平台退 300
        assertTrue(innerRefund(refundId, orderId, merchantId, 3_000L), "首次冲减应生效");
        assertEquals(6_300L, merchantBalance(merchantId), "商家余额应扣减 2700");
        assertEquals(2_700L, queryLong("SELECT merchant_share FROM mall_merchant_refund_adjust"
                + " WHERE refund_id = ? AND deleted = 0", refundId));
        assertEquals(300L, queryLong("SELECT platform_share FROM mall_merchant_refund_adjust"
                + " WHERE refund_id = ? AND deleted = 0", refundId));

        // 同一售后单重复回调：幂等，不再扣
        assertFalse(innerRefund(refundId, orderId, merchantId, 3_000L), "同一售后单重复冲减应被跳过");
        assertEquals(6_300L, merchantBalance(merchantId));

        // 冲减不超过商家应收：再退 10000 也只扣到 6300 为止
        long another = orderSeq.incrementAndGet();
        innerRefund(another, orderId, merchantId, 10_000L);
        assertEquals(0L, merchantBalance(merchantId), "冲减后余额不得为负");
    }

    @Test
    void refundIgnoredWhenOrderNotSettled() throws Exception {
        long merchantId = applyAndEnable("测试未结算店", 100);
        long orderId = orderSeq.incrementAndGet();
        long refundId = orderSeq.incrementAndGet();
        // 订单未完成结算（无明细）→ 冲减直接跳过，不影响余额
        assertFalse(innerRefund(refundId, orderId, merchantId, 1_000L));
        assertEquals(0L, merchantBalance(merchantId));
    }

    @Test
    void withdrawalFreezesBalanceAndRejectRestores() throws Exception {
        long merchantId = applyAndEnable("测试提现店", 100);
        long orderId = orderSeq.incrementAndGet();
        innerSettle(orderId, merchantId, 10_000L); // 余额 9000

        // 超额提现 → 400，余额不变
        bizError(mvc.perform(post("/mall_merchant_withdrawal/apply")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantId", String.valueOf(merchantId), "amount", 9_001L,
                        "method", 0)))), 400);
        assertEquals(9_000L, merchantBalance(merchantId));

        // 正常提现 5000 → 余额冻结到 4000
        JsonNode applied = ok(mvc.perform(post("/mall_merchant_withdrawal/apply")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantId", String.valueOf(merchantId), "amount", 5_000L,
                        "method", 1)))));
        String withdrawalId = applied.get("data").get("id").asText();
        assertEquals(4_000L, merchantBalance(merchantId), "申请即冻结余额");

        // 审核拒绝 → 余额原路退回
        ok(mvc.perform(post("/mall_merchant_withdrawal/audit")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", withdrawalId, "pass", false, "rejectReason", "资料不全")))));
        assertEquals(9_000L, merchantBalance(merchantId), "拒绝后余额应退回");
        assertEquals(3, queryInt("SELECT status FROM mall_merchant_withdrawal WHERE id = ?",
                Long.parseLong(withdrawalId)));
    }

    @Test
    void settlementAggregatesPendingDetails() throws Exception {
        long merchantId = applyAndEnable("测试结算单店", 50); // 5%
        long order1 = orderSeq.incrementAndGet();
        long order2 = orderSeq.incrementAndGet();
        innerSettle(order1, merchantId, 10_000L); // 抽 500，商家 9500
        innerSettle(order2, merchantId, 20_000L); // 抽 1000，商家 19000

        JsonNode built = ok(mvc.perform(post("/mall_merchant_settlement/build")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantId", String.valueOf(merchantId))))));
        JsonNode settlement = built.get("data");
        assertEquals(2, settlement.get("orderCount").asInt(), "两笔订单应聚合在同一张结算单");
        assertEquals(28_500L, settlement.get("goodsAmount").asLong(), "商家应收合计 9500+19000");
        assertEquals(1_500L, settlement.get("platformFee").asLong(), "平台抽成合计 500+1000");
        assertEquals(28_500L, settlement.get("actualAmount").asLong());

        String settlementId = settlement.get("id").asText();
        JsonNode details = ok(mvc.perform(get("/mall_merchant_settlement/" + settlementId + "/details")
                .header("Authorization", bearer(adminToken()))));
        assertEquals(2, details.get("data").size(), "明细应归集到该结算单");

        // 没有待出账明细时再生成 → 400
        bizError(mvc.perform(post("/mall_merchant_settlement/build")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantId", String.valueOf(merchantId))))), 400);
    }

    @Test
    void shopEndpointOnlyExposesEnabledMerchants() throws Exception {
        long enabled = applyAndEnable("测试店铺展示", 50);
        long pending = applyMerchant("测试待审店铺", "LIC-SHOP-" + System.currentTimeMillis(), 50);

        ok(mvc.perform(get("/shop/merchant/" + enabled)));
        bizError(mvc.perform(get("/shop/merchant/" + pending)), 400);
        bizError(mvc.perform(get("/shop/merchant/999999999")), 400);
    }

    // ===== 辅助 =====

    private long applyMerchant(String name, String licenseNo, int rate) throws Exception {
        JsonNode root = ok(mvc.perform(post("/mall_merchant/apply")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(applyBody(name, licenseNo, rate)))));
        long id = Long.parseLong(root.get("data").get("id").asText());
        merchantIds.add(id);
        return id;
    }

    private long applyAndEnable(String name, int rate) throws Exception {
        long id = applyMerchant(name, "LIC-" + System.nanoTime(), rate);
        ok(mvc.perform(post("/mall_merchant/audit")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("id", String.valueOf(id), "pass", true)))));
        assertEquals(1, merchantStatus(id));
        return id;
    }

    private Map<String, Object> applyBody(String name, String licenseNo, int rate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("legalName", name + "有限公司");
        body.put("licenseNo", licenseNo);
        body.put("contactName", "测试联系人");
        body.put("contactPhone", "13900000000");
        body.put("category", "数码");
        body.put("settleRate", rate);
        return body;
    }

    private boolean innerSettle(long orderId, long merchantId, long payAmount) throws Exception {
        MvcResult result = mvc.perform(post("/inner/merchant/settle")
                        .param("orderId", String.valueOf(orderId))
                        .param("merchantId", String.valueOf(merchantId))
                        .param("orderNo", "T" + orderId)
                        .param("payAmount", String.valueOf(payAmount)))
                .andExpect(status().isOk()).andReturn();
        return Boolean.parseBoolean(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean innerRefund(long refundId, long orderId, long merchantId, long refundAmount) throws Exception {
        MvcResult result = mvc.perform(post("/inner/merchant/refund-adjust")
                        .param("refundId", String.valueOf(refundId))
                        .param("orderId", String.valueOf(orderId))
                        .param("merchantId", String.valueOf(merchantId))
                        .param("refundAmount", String.valueOf(refundAmount)))
                .andExpect(status().isOk()).andReturn();
        return Boolean.parseBoolean(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int merchantStatus(long id) {
        return queryInt("SELECT status FROM mall_merchant WHERE id = ?", id);
    }

    private long merchantBalance(long id) {
        return queryLong("SELECT account_balance FROM mall_merchant WHERE id = ?", id);
    }

    private int queryInt(String sql, Object arg) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arg);
        return value == null ? -1 : value;
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? -1L : value;
    }
}
