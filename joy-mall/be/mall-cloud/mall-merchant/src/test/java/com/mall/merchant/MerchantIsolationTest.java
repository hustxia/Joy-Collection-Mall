package com.mall.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V11 商家侧闭环集成测试：三方令牌分流 + 数据隔离 + 越权留痕。
 *
 * <p>这个类只测「边界」，不测业务：商家能不能进、进来能不能看见别人家的数据、
 * 被挡住时是 401 还是 403。业务链路（发品/发货/结算）由各域自己的用例覆盖。
 *
 * <p>断言里刻意区分 401 与 403：
 * 401 = 没身份或身份过期（可以再登录），403 = 有身份但越界（再登录也没用）。
 * 两者混用会让前端做出错误引导（把越权当成掉线去重登）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MerchantIsolationTest extends MallIntegrationTestBase {

    private static final String PASSWORD = "123456";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    private final Set<Long> merchantIds = new LinkedHashSet<>();
    private final Set<Long> orderIds = new LinkedHashSet<>();

    private long merchantA;
    private long merchantB;
    private String usernameA;
    private String usernameB;

    @BeforeAll
    void setUp() {
        merchantA = createMerchant("A");
        merchantB = createMerchant("B");
        usernameA = createUser(merchantA, "mch_a_" + SEQ.incrementAndGet());
        usernameB = createUser(merchantB, "mch_b_" + SEQ.incrementAndGet());
    }

    @AfterAll
    void tearDown() {
        for (Long id : orderIds) {
            jdbc.update("DELETE FROM mall_order WHERE id = ?", id);
        }
        for (Long id : merchantIds) {
            jdbc.update("DELETE FROM mall_merchant_user WHERE merchant_id = ?", id);
            jdbc.update("DELETE FROM mall_merchant WHERE id = ?", id);
        }
        orderIds.clear();
        merchantIds.clear();
        tearDownFixtures();
    }

    @Test
    void merchantCanLoginAndGetsOwnShop() throws Exception {
        // ok() 返回整包 Result，业务数据在 data 下
        JsonNode data = ok(post("/merchant-admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", usernameA, "password", PASSWORD)))).path("data");

        assertTrue(data.path("token").asText().length() > 10, "登录应发商家令牌");
        assertEquals(String.valueOf(merchantA), data.path("merchant").path("id").asText(),
                "令牌里的店铺必须是账号所属店铺，不能由前端指定");
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        bizError(post("/merchant-admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", usernameA, "password", "wrong-pwd"))), 400);
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/merchant-admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    /** 平台令牌打商家后台：403 而不是 401——身份是有效的，只是走错了门 */
    @Test
    void platformTokenCannotOpenMerchantAdmin() throws Exception {
        mvc.perform(get("/merchant-admin/overview").header("Authorization", bearer(adminToken())))
                .andExpect(status().isForbidden());
    }

    /** 商家令牌打平台接口：同样 403。两侧令牌不通用是硬约束，靠前缀分流而不是权限点 */
    @Test
    void merchantTokenCannotCallPlatformApi() throws Exception {
        String token = loginToken(usernameA);
        mvc.perform(get("/mall_merchant/page")
                        .header("Merchant-Token", token)
                        .param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isForbidden());
    }

    /** 商家令牌塞进 Authorization 也进不来（防绕过 Merchant-Token 分流） */
    @Test
    void merchantTokenInAuthorizationHeaderIsRejected() throws Exception {
        String token = loginToken(usernameA);
        mvc.perform(get("/mall_merchant/page").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    /** 越权访问别家订单：403 + 落一条越权日志（可审计） */
    @Test
    void crossShopOrderIsForbiddenAndLogged() throws Exception {
        long orderOfB = createOrder(merchantB);
        String token = loginToken(usernameA);

        // 业务异常统一走 Result 包装：HTTP 仍是 200，越权语义在 body 的 code 上
        bizError(get("/merchant-admin/order/" + orderOfB).header("Merchant-Token", token), 403);

        Integer logs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE status = 0 AND error_msg LIKE ?",
                Integer.class, "%归属商家 " + merchantB + "%");
        assertTrue(logs != null && logs > 0, "越权被拦截后必须留痕，否则事后无法追查是谁在探测别家数据");
    }

    /** 概览只统计本店：两店各 1 单，A 的待发货数不能把 B 算进来 */
    @Test
    void overviewCountsOwnShopOnly() throws Exception {
        long orderA = createOrder(merchantA);
        createOrder(merchantB);
        String token = loginToken(usernameA);

        JsonNode data = ok(get("/merchant-admin/overview").header("Merchant-Token", token)).path("data");
        assertTrue(data.path("pendingShipCount").asLong() >= 1, "本店待发货应至少 1 单");
        assertEquals(String.valueOf(merchantA),
                String.valueOf(jdbc.queryForObject("SELECT merchant_id FROM mall_order WHERE id = ?", Long.class, orderA)),
                "夹具自检：订单确实挂在 A 名下");
    }

    // ===== 夹具 =====

    private long createMerchant(String tag) {
        long id = SEQ.incrementAndGet();
        jdbc.update("INSERT INTO mall_merchant (id, merchant_no, name, settle_rate, status, account_balance,"
                        + " settled_total, deleted, create_time, update_time) VALUES (?,?,?,?,?,?,?,0,NOW(),NOW())",
                id, "MCH" + tag + id, "测试店铺" + tag, 50, 1, 100000L, 0L);
        merchantIds.add(id);
        return id;
    }

    /** 商家账号：密码用 sha256 存储，与平台早期账号同算法（服务层两种都支持） */
    private String createUser(long merchantId, String username) {
        jdbc.update("INSERT INTO mall_merchant_user (id, merchant_id, username, password, password_algo, role,"
                        + " status, fail_count, deleted, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,0,NOW(),NOW())",
                SEQ.incrementAndGet(), merchantId, username, sha256(PASSWORD), "sha256", "owner", 1, 0);
        return username;
    }

    private long createOrder(long merchantId) {
        long id = SEQ.incrementAndGet();
        jdbc.update("INSERT INTO mall_order (id, order_no, member_id, merchant_id, total_amount, freight_amount,"
                        + " discount_amount, pay_amount, status, pay_status, receiver_name, receiver_phone,"
                        + " receiver_address, deleted, order_type) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,0)",
                id, "V11T" + id, 1L, merchantId, 10000L, 0L, 0L, 10000L, 20, 1, "张三", "13800000000", "测试地址");
        orderIds.add(id);
        return id;
    }

    private String loginToken(String username) throws Exception {
        JsonNode data = ok(post("/merchant-admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", PASSWORD)))).path("data");
        return data.path("token").asText();
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
