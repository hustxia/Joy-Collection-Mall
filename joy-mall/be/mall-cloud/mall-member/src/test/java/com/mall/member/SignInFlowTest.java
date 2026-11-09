package com.mall.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 签到回归：连签天数 = 昨天有记录 ? 昨天+1 : 1；赠送积分 = min(连签天数, 7)；
 * 签到记录与积分流水同一事务。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignInFlowTest extends MallIntegrationTestBase {

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM mall_sign_in WHERE member_id IN"
                + " (SELECT id FROM mall_member WHERE nickname LIKE '" + TEST_MARK + "%')");
        jdbc.update("DELETE FROM mall_member_point_log WHERE member_id IN"
                + " (SELECT id FROM mall_member WHERE nickname LIKE '" + TEST_MARK + "%')");
        tearDownFixtures();
    }

    /** 首次签到：连签 1 天送 1 分，签到记录与积分流水同时落库 */
    @Test
    void firstSignInGrantsOnePoint() throws Exception {
        long member = createMember("签到首签");
        String token = memberToken(member);

        JsonNode data = signIn(token);
        assertEquals(1, data.get("continuousDays").asInt());
        assertEquals(1, data.get("points").asInt());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_sign_in WHERE member_id = ? AND sign_date = CURDATE()"
                        + " AND continuous_days = 1 AND points = 1", Integer.class, member));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_member_point_log WHERE member_id = ? AND change_type = 2"
                        + " AND points = 1 AND balance_after = 1 AND remark = '每日签到'",
                Integer.class, member), "签到送积分走积分发放（changeType=2）");
        assertEquals(1, jdbc.queryForObject(
                "SELECT points FROM mall_member WHERE id = ?", Integer.class, member));
    }

    /** 今日已签到 → 400，余额与签到记录不变 */
    @Test
    void duplicateSignInSameDayFails() throws Exception {
        long member = createMember("签到重复");
        String token = memberToken(member);
        signIn(token);

        JsonNode fail = bizError(post("/shop/signin").header("Authorization", bearer(token)), 400);
        assertEquals("今日已签到", fail.get("msg").asText());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_sign_in WHERE member_id = ?", Integer.class, member), "不重复落签到记录");
        assertEquals(1, jdbc.queryForObject(
                "SELECT points FROM mall_member WHERE id = ?", Integer.class, member), "不重复送分");
    }

    /** 昨天连签 3 天 → 今天第 4 天送 4 分 */
    @Test
    void continuousStreakAccumulatesFromYesterday() throws Exception {
        long member = createMember("签到连签");
        String token = memberToken(member);
        seedSign(member, LocalDate.now().minusDays(1), 3, 3);

        JsonNode data = signIn(token);
        assertEquals(4, data.get("continuousDays").asInt());
        assertEquals(4, data.get("points").asInt());
        assertEquals(4, jdbc.queryForObject(
                "SELECT points FROM mall_member WHERE id = ?", Integer.class, member));
    }

    /** 前天有记录、昨天没有 → 断签，今天重新从 1 天 1 分开始 */
    @Test
    void streakBreaksWhenYesterdayMissing() throws Exception {
        long member = createMember("签到断签");
        String token = memberToken(member);
        seedSign(member, LocalDate.now().minusDays(2), 5, 5);

        JsonNode data = signIn(token);
        assertEquals(1, data.get("continuousDays").asInt(), "断签重新计 1 天");
        assertEquals(1, data.get("points").asInt());
    }

    /** 连签奖励封顶：昨天已连签 7 天 → 今天第 8 天仍只送 7 分 */
    @Test
    void pointsCapAtSeven() throws Exception {
        long member = createMember("签到封顶");
        String token = memberToken(member);
        seedSign(member, LocalDate.now().minusDays(1), 7, 7);

        JsonNode data = signIn(token);
        assertEquals(8, data.get("continuousDays").asInt());
        assertEquals(7, data.get("points").asInt(), "积分按 min(连签天数, 7) 封顶");
    }

    /** 签到日历：当月已签日期列表 + 今天是否已签 */
    @Test
    void calendarShowsSignedDays() throws Exception {
        long member = createMember("签到日历");
        String token = memberToken(member);
        signIn(token);

        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        JsonNode data = ok(get("/shop/signin/calendar?month=" + month)
                .header("Authorization", bearer(token))).get("data");
        assertTrue(data.get("todaySigned").asBoolean());
        assertEquals(1, data.get("days").size());
        assertEquals(LocalDate.now().toString(), data.get("days").get(0).asText());

        JsonNode empty = ok(get("/shop/signin/calendar?month=2020-01")
                .header("Authorization", bearer(token))).get("data");
        assertEquals(0, empty.get("days").size(), "其他月份无记录");
        assertTrue(empty.get("todaySigned").asBoolean(), "todaySigned 与查询月份无关");

        bizError(get("/shop/signin/calendar?month=abc").header("Authorization", bearer(token)), 400);
    }

    /** 未登录签到 → 401 */
    @Test
    void anonymousRejected() throws Exception {
        mvc.perform(post("/shop/signin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/shop/signin/calendar?month=2026-11")).andExpect(status().isUnauthorized());
    }

    // ===== 小工具 =====

    private JsonNode signIn(String token) throws Exception {
        return ok(post("/shop/signin").header("Authorization", bearer(token))).get("data");
    }

    private void seedSign(long memberId, LocalDate date, int continuousDays, int points) {
        jdbc.update("INSERT INTO mall_sign_in (member_id, sign_date, continuous_days, points)"
                        + " VALUES (?,?,?,?)", memberId, date.toString(), continuousDays, points);
    }
}
