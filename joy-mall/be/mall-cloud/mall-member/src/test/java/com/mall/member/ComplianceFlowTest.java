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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V11 合规三件套集成测试（REQ-P0-06）：协议留痕 / 注销冷静期 / 个人信息导出。
 *
 * <p>合规的本质是「不可抵赖」与「可恢复」：
 * 协议同意必须落版本号（否则日后改协议就没法证明用户同意的是哪一版）；
 * 注销必须先冷静期、可撤销（否则手滑一次账号就没了）；导出只能含本人数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceFlowTest extends MallIntegrationTestBase {

    private final Set<Long> memberIds = new LinkedHashSet<>();

    /** 注册接口校验 ^1\d{10}$，必须 11 位（基类 newPhone 是 13 位，只用于直接落库的夹具） */
    private static String newRegisterPhone() {
        return String.format("13%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    @AfterAll
    void tearDown() {
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM mall_member_agreement_log WHERE member_id = ?", id);
            jdbc.update("DELETE FROM mall_address WHERE member_id = ?", id);
            jdbc.update("DELETE FROM mall_member WHERE id = ?", id);
        }
        memberIds.clear();
        tearDownFixtures();
    }

    /** 未勾选协议注册：400（服务端才是边界，前端 checkbox 只是提示） */
    @Test
    void registerWithoutAgreementIsRejected() throws Exception {
        String phone = newRegisterPhone();
        JsonNode root = bizError(post("/shop/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phone", phone, "password", "123456", "agreed", false))), 400);
        assertTrue(root.path("msg").asText().contains("同意用户协议"),
                "提示要指向真正的动作，不能只说「参数错误」");
    }

    /** 勾选协议注册：成功，并按当前生效版本各留一条同意记录（用户协议 + 隐私政策） */
    @Test
    void registerWithAgreementRecordsVersion() throws Exception {
        String phone = newRegisterPhone();
        JsonNode data = ok(post("/shop/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phone", phone, "password", "123456", "agreed", true)))).path("data");
        long memberId = Long.parseLong(data.path("member").path("id").asText());
        memberIds.add(memberId);

        Integer logs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_member_agreement_log WHERE member_id = ? AND version IS NOT NULL",
                Integer.class, memberId);
        assertTrue(logs != null && logs >= 2, "用户协议与隐私政策各要留一条带版本号的同意记录");
    }

    /** 注销：申请进入冷静期 → 撤销恢复；再次申请且到期后执行脱敏 */
    @Test
    void cancelGoesThroughCoolingPeriod() throws Exception {
        long memberId = createMember("cancel");
        String token = memberToken(memberId);
        memberIds.add(memberId);

        JsonNode applied = ok(post("/shop/member/cancel/apply").header("Authorization", bearer(token))).path("data");
        assertEquals(1, applied.path("cancelStatus").asInt(), "申请后应处于冷静期");
        assertTrue(applied.path("remainDays").asInt() > 0, "冷静期必须给出剩余天数，否则用户不知道何时生效");

        JsonNode revoked = ok(post("/shop/member/cancel/revoke").header("Authorization", bearer(token))).path("data");
        assertEquals(0, revoked.path("cancelStatus").asInt(), "冷静期内撤销应恢复为正常账号");

        // 二次申请后把到期时间拨到过去，验证「到期才执行」而不是「申请即注销」
        ok(post("/shop/member/cancel/apply").header("Authorization", bearer(token)));
        jdbc.update("UPDATE mall_member SET cancel_effective_time = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE id = ?", memberId);

        JsonNode doned = ok(get("/shop/member/cancel/status").header("Authorization", bearer(token))).path("data");
        assertEquals(2, doned.path("cancelStatus").asInt(), "到期后再次访问应执行注销（惰性执行）");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT phone, nickname, cancel_status FROM mall_member WHERE id = ?", memberId);
        assertNotEquals(String.valueOf(row.get("phone")).length(), 0);
        // 占位必须「不是手机号」，否则日后会被当成真实号码拨打/群发
        assertFalse(String.valueOf(row.get("phone")).matches("^1\\d{10}$"),
                "注销后手机号必须换成不可用的占位：" + row.get("phone"));
        assertEquals(2, ((Number) row.get("cancel_status")).intValue());
    }

    /** 个人信息导出：只含本人资料与地址 */
    @Test
    void exportContainsOnlyOwnData() throws Exception {
        long me = createMember("export");
        long other = createMember("other");
        memberIds.add(me);
        memberIds.add(other);
        // 直接落库造地址：收件人名字带用途，导出串数据时断言能立刻看出是谁的
        insertAddress(me, "本人地址一");
        insertAddress(me, "本人地址二");
        insertAddress(other, "他人地址");

        String token = memberToken(me);
        JsonNode data = ok(get("/shop/member/export").header("Authorization", bearer(token))).path("data");

        assertEquals(String.valueOf(me), data.path("profile").path("id").asText(), "导出主体的 id 必须是本人");
        assertEquals(2, data.path("addresses").size(), "只应导出本人 2 条地址，不能把别人的带出来");
        for (JsonNode addr : data.path("addresses")) {
            assertTrue(addr.path("receiver_name").asText().startsWith("本人地址"),
                    "导出内容混入了他人地址：" + addr.path("receiver_name").asText());
        }
        assertEquals(2, data.path("summary").path("addressCount").asInt(), "汇总口径要与明细条数一致");
    }

    private void insertAddress(long memberId, String receiverName) {
        jdbc.update("INSERT INTO mall_address (id, member_id, receiver_name, receiver_phone, province, city, district,"
                        + " detail, is_default, deleted, create_time, update_time)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,0,NOW(),NOW())",
                System.nanoTime() % 1_000_000_000L, memberId, receiverName, "13900000000",
                "广东省", "深圳市", "南山区", "测试路 1 号", 0);
    }

    /** 注销期间会话仍可用（订单可查），但状态接口要明确告知处于冷静期 */
    @Test
    void coolingPeriodIsVisibleToMember() throws Exception {
        long memberId = createMember("cooling");
        String token = memberToken(memberId);
        memberIds.add(memberId);

        ok(post("/shop/member/cancel/apply").header("Authorization", bearer(token)));
        JsonNode status = ok(get("/shop/member/cancel/status").header("Authorization", bearer(token))).path("data");
        assertEquals(1, status.path("cancelStatus").asInt());
        assertTrue(status.path("effectiveTime").asText().length() >= 10, "要能告诉用户哪天正式注销");
    }
}
