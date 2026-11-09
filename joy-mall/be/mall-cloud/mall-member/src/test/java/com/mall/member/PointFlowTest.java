package com.mall.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 积分回归：发放走「条件 UPDATE 余额 + 写流水」，
 * 余额不足（扣成负数）必须业务失败且余额/流水都不动。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointFlowTest extends MallIntegrationTestBase {

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM mall_member_point_log WHERE member_id IN"
                + " (SELECT id FROM mall_member WHERE nickname LIKE '" + TEST_MARK + "%')");
        tearDownFixtures();
    }

    /** 发放积分：余额增加、流水落库（balance_after=变动后余额）、C 端可查 */
    @Test
    void grantAddsBalanceAndWritesLog() throws Exception {
        long member = createMember("积分发放");
        String token = memberToken(member);

        assertEquals(100, grant(member, 1, 100, null), "返回变动后余额");
        assertEquals(100, balance(token));

        JsonNode logs = logs(token);
        assertEquals(1, logs.get("total").asInt());
        JsonNode row = logs.get("list").get(0);
        assertEquals(1, row.get("changeType").asInt());
        assertEquals(100, row.get("points").asInt());
        assertEquals(100, row.get("balanceAfter").asInt());
        assertEquals("消费返测试", row.get("remark").asText());

        assertEquals(100, jdbc.queryForObject(
                "SELECT points FROM mall_member WHERE id = ?", Integer.class, member));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_member_point_log WHERE member_id = ? AND change_type = 1",
                Integer.class, member));
    }

    /** 扣成负数必须失败：余额不变、不写流水；扣到 0 允许 */
    @Test
    void deductBelowZeroIsRejected() throws Exception {
        long member = createMember("积分扣减");
        String token = memberToken(member);

        assertEquals(30, grant(member, 1, 30, null));
        JsonNode fail = grantFail(member, 3, -50);
        assertEquals("积分不足", fail.get("msg").asText());
        assertEquals(30, balance(token), "失败不改余额");
        assertEquals(1, logs(token).get("total").asInt(), "失败不写流水");

        assertEquals(0, grant(member, 3, -30, null), "扣到 0 允许");
        grantFail(member, 3, -1);
        assertEquals(0, balance(token));
    }

    /** 流水分页新→旧 */
    @Test
    void logsAreNewestFirst() throws Exception {
        long member = createMember("积分流水");
        String token = memberToken(member);
        grant(member, 1, 10, null);
        grant(member, 1, 20, null);
        grant(member, 1, 30, null);

        JsonNode logs = logs(token);
        assertEquals(3, logs.get("total").asInt());
        assertEquals(30, logs.get("list").get(0).get("points").asInt(), "最新的在最前");
        assertEquals(60, logs.get("list").get(0).get("balanceAfter").asInt(), "流水余额逐笔递进");
        assertEquals(10, logs.get("list").get(2).get("points").asInt());
    }

    /** 会员不存在 → 400，不落任何数据 */
    @Test
    void grantUnknownMemberFails() throws Exception {
        bizError(post("/inner/point/grant").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":99999999,\"changeType\":1,\"points\":10}"), 400);
    }

    /** 未登录查余额/流水 → 401 */
    @Test
    void anonymousRejected() throws Exception {
        mvc.perform(get("/shop/point/balance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/shop/point/logs")).andExpect(status().isUnauthorized());
    }

    // ===== 小工具 =====

    /** 内部发放（/inner 免鉴权，成功返回原始载荷 {points: 余额}） */
    private int grant(long memberId, int changeType, int points, Long orderId) throws Exception {
        String orderPart = orderId == null ? "" : ",\"orderId\":" + orderId;
        MvcResult result = mvc.perform(post("/inner/point/grant").characterEncoding("UTF-8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberId + ",\"changeType\":" + changeType
                                + ",\"points\":" + points + orderPart + ",\"remark\":\"消费返测试\"}"))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("points").asInt();
    }

    /** 发放失败：全局异常处理包成 Result（code=400） */
    private JsonNode grantFail(long memberId, int changeType, int points) throws Exception {
        return bizError(post("/inner/point/grant").characterEncoding("UTF-8")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":" + memberId + ",\"changeType\":" + changeType
                        + ",\"points\":" + points + ",\"remark\":\"消费返测试\"}"), 400);
    }

    private int balance(String token) throws Exception {
        return ok(get("/shop/point/balance").header("Authorization", bearer(token)))
                .get("data").get("points").asInt();
    }

    private JsonNode logs(String token) throws Exception {
        return ok(get("/shop/point/logs").header("Authorization", bearer(token))).get("data");
    }
}
