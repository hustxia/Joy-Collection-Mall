package com.mall.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.service.MallCommissionRecordService;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V9 分销全链路集成测试：申请/审核、绑定防自邀/防重复、佣金计算、结算幂等、退款回退、提现扣减。
 * 跨服务 member 调用全部 @MockBean（余额增减），用例只依赖「本服务 + 测试库」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributorFlowTest extends MallIntegrationTestBase {

    @MockBean
    private MemberInnerClient memberInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;
    @MockBean
    private OrderInnerClient orderInnerClient;

    @Autowired
    private MallCommissionRecordService commissionRecordService;

    private long memberA;
    private long memberB;
    private long goodsId;
    private long skuId;
    private final AtomicLong orderSeq = new AtomicLong(System.currentTimeMillis());

    @BeforeAll
    void prepare() {
        memberA = createMember("分销A");
        memberB = createMember("分销B");
        skuId = createGoodsWithSku("分销商品", 100000, 10);
        goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        // 全局佣金规则 100‰（测试库不灌种子，自建且每次重置为唯一）
        jdbc.update("DELETE FROM mall_commission_rule WHERE scope = 0");
        jdbc.update("INSERT INTO mall_commission_rule (scope, rate, fixed, level, status) VALUES (0, 100, 0, 1, 1)");
    }

    @BeforeEach
    void stub() {
        lenient().when(memberInnerClient.commissionBalanceAdjust(anyLong(), anyLong())).thenReturn(true);
        lenient().when(memberInnerClient.commissionBalanceAdjust(anyLong(), eq(-999999999L))).thenReturn(false);
        lenient().when(memberInnerClient.pushNotice(any())).thenReturn(1L);
    }

    @Test
    void applyAndAudit() throws Exception {
        JsonNode apply = body(postJson("/shop/distributor/apply", memberToken(memberA), null));
        assertEquals(200, apply.get("code").asInt());
        String code = apply.get("data").get("inviteCode").asText();
        assertTrue(code != null && code.length() == 8, "邀请码应为8位");

        // 重复申请返回同一邀请码（幂等）
        JsonNode apply2 = body(postJson("/shop/distributor/apply", memberToken(memberA), null));
        assertEquals(code, apply2.get("data").get("inviteCode").asText());

        // 后台审核通过 → status=1
        Long distId = jdbc.queryForObject("SELECT id FROM mall_distributor WHERE member_id = ?", Long.class, memberA);
        ok(mvc.perform(post("/mall_distributor/audit")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + distId + ",\"pass\":true}")));
        assertEquals(1, (int) jdbc.queryForObject("SELECT status FROM mall_distributor WHERE id = ?", Integer.class, distId));

        // 重置累计/可提现与历史佣金、提现记录，确保各用例相互隔离
        // （@TestInstance(PER_CLASS) 下 4 个用例共用同一分销员，不重置则结算/提现会跨用例累加污染断言）
        jdbc.update("UPDATE mall_distributor SET total_commission=0, settled=0, withdrawable=0 WHERE id = ?", distId);
        jdbc.update("DELETE FROM mall_commission_record WHERE distributor_id = ?", distId);
        jdbc.update("DELETE FROM mall_withdrawal WHERE distributor_id = ?", distId);
    }

    @Test
    void bindRules() throws Exception {
        applyAndAudit();
        String codeA = jdbc.queryForObject("SELECT invite_code FROM mall_distributor WHERE member_id = ?", String.class, memberA);

        // B 绑定 A 的码 → 成功
        okJson("/shop/distributor/bind", memberToken(memberB),
                json(Map.of("inviteCode", codeA, "channel", "code")));
        assertNotNull(jdbc.queryForObject("SELECT id FROM mall_invitation WHERE invitee_id = ? AND deleted = 0",
                Long.class, memberB));

        // B 重复绑定 → 400
        bizErrorJson("/shop/distributor/bind", memberToken(memberB),
                json(Map.of("inviteCode", codeA)), 400);

        // A 用自己码自邀 → 400
        bizErrorJson("/shop/distributor/bind", memberToken(memberA),
                json(Map.of("inviteCode", codeA)), 400);
    }

    @Test
    void settleIdempotentAndRefund() throws Exception {
        applyAndAudit();
        Long distId = jdbc.queryForObject("SELECT id FROM mall_distributor WHERE member_id = ?", Long.class, memberA);
        long orderId = orderSeq.incrementAndGet();

        settle(distId, orderId, 100000);
        long amount = jdbc.queryForObject("SELECT amount FROM mall_commission_record WHERE order_id = ? AND distributor_id = ?",
                Long.class, orderId, distId);
        assertEquals(10000, amount, "100000 * 100‰ = 10000");
        assertEquals(10000, (long) jdbc.queryForObject("SELECT withdrawable FROM mall_distributor WHERE id = ?",
                Long.class, distId));

        // 幂等：重复结算不产生新记录
        settle(distId, orderId, 100000);
        int cnt = jdbc.queryForObject("SELECT COUNT(*) FROM mall_commission_record WHERE order_id = ? AND distributor_id = ?",
                Integer.class, orderId, distId);
        assertEquals(1, cnt);

        // 退款失效 + 回退可提现
        commissionRecordService.invalid(orderId, distId);
        assertEquals(2, (int) jdbc.queryForObject("SELECT status FROM mall_commission_record WHERE order_id = ?",
                Integer.class, orderId));
        assertEquals(0, (long) jdbc.queryForObject("SELECT withdrawable FROM mall_distributor WHERE id = ?",
                Long.class, distId));
    }

    @Test
    void withdraw() throws Exception {
        applyAndAudit();
        Long distId = jdbc.queryForObject("SELECT id FROM mall_distributor WHERE member_id = ?", Long.class, memberA);
        settle(distId, orderSeq.incrementAndGet(), 100000);
        assertTrue((long) jdbc.queryForObject("SELECT withdrawable FROM mall_distributor WHERE id = ?",
                Long.class, distId) >= 5000);

        okJson("/shop/distributor/withdraw", memberToken(memberA),
                json(Map.of("amount", 5000, "method", 0)));
        assertEquals(0, (int) jdbc.queryForObject("SELECT status FROM mall_withdrawal WHERE distributor_id = ?",
                Integer.class, distId));

        // 超额提现 → 余额不足
        bizErrorJson("/shop/distributor/withdraw", memberToken(memberA),
                json(Map.of("amount", 999999999L, "method", 0)), 400);
    }

    /** postJson 内部已 perform，直接断言业务 code=200 并返回响应体 */
    private JsonNode okJson(String url, String token, String body) throws Exception {
        JsonNode root = body(postJson(url, token, body));
        assertEquals(200, root.get("code").asInt(), "期望业务 code=200，实际：" + root);
        return root;
    }

    /** postJson 内部已 perform，断言业务 code 为期望失败码并返回响应体 */
    private JsonNode bizErrorJson(String url, String token, String body, int expectedCode) throws Exception {
        JsonNode root = body(postJson(url, token, body));
        assertEquals(expectedCode, root.get("code").asInt(), "期望业务 code=" + expectedCode + "，实际：" + root);
        return root;
    }

    private void settle(long distId, long orderId, long payAmount) throws Exception {
        mvc.perform(post("/mall_commission/settle")
                .header("Authorization", bearer(adminToken()))
                .param("distributorId", String.valueOf(distId))
                .param("orderId", String.valueOf(orderId))
                .param("memberId", String.valueOf(memberA))
                .param("goodsId", String.valueOf(goodsId))
                .param("payAmount", String.valueOf(payAmount)));
    }
}
