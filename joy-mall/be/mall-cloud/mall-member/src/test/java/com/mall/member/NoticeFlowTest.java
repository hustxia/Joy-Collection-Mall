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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 站内消息回归：推送走 /inner/notice/push（免鉴权），已读走条件更新（仅本人），
 * 后台 /mall_notice/page 只读台账。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoticeFlowTest extends MallIntegrationTestBase {

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM mall_notice WHERE member_id IN"
                + " (SELECT id FROM mall_member WHERE nickname LIKE '" + TEST_MARK + "%')");
        tearDownFixtures();
    }

    /** 推送 → 列表可见（新→旧）+ 未读数 +1 */
    @Test
    void pushThenListAndUnreadCount() throws Exception {
        long member = createMember("消息推送");
        String token = memberToken(member);

        long id = push(member, 1, "订单支付成功", "您的订单 MO001 已支付", "MO001");
        assertTrue(id > 0);

        JsonNode list = list(token, null);
        assertEquals(1, list.get("total").asInt());
        JsonNode row = list.get("list").get(0);
        assertEquals(String.valueOf(id), row.get("id").asText());
        assertEquals(1, row.get("type").asInt());
        assertEquals("订单支付成功", row.get("title").asText());
        assertEquals("MO001", row.get("bizId").asText());
        assertEquals(0, row.get("readStatus").asInt(), "新消息未读");
        assertEquals(1, unread(token));
    }

    /** 越权已读他人消息 → 403；本人已读成功且幂等 */
    @Test
    void markReadOnlyOwn() throws Exception {
        long owner = createMember("消息归属A");
        long other = createMember("消息归属B");
        String ownerToken = memberToken(owner);
        String otherToken = memberToken(other);
        long noticeId = push(owner, 1, "订单已发货", "", "MO002");

        bizError(post("/shop/notice/read/" + noticeId).header("Authorization", bearer(otherToken)), 403);
        assertEquals(1, unread(ownerToken), "他人已读不得生效");

        ok(post("/shop/notice/read/" + noticeId).header("Authorization", bearer(ownerToken)));
        assertEquals(0, unread(ownerToken));
        // 重复已读幂等成功
        ok(post("/shop/notice/read/" + noticeId).header("Authorization", bearer(ownerToken)));
        assertEquals(0, unread(ownerToken));
    }

    /** 全部已读：返回影响行数，重复调用为 0 */
    @Test
    void readAllMarksEverything() throws Exception {
        long member = createMember("消息全读");
        String token = memberToken(member);
        push(member, 1, "订单支付成功", "", "MO003");
        push(member, 2, "售后已通过", "", "AS002");
        push(member, 3, "签到有礼", "", "");

        JsonNode data = ok(post("/shop/notice/read-all").header("Authorization", bearer(token))).get("data");
        assertEquals(3, data.get("read").asInt());
        assertEquals(0, unread(token));
        assertEquals(0, ok(post("/shop/notice/read-all").header("Authorization", bearer(token)))
                .get("data").get("read").asInt(), "重复全读不再影响行数");
    }

    /** 列表按类型筛选 */
    @Test
    void listFiltersByType() throws Exception {
        long member = createMember("消息筛选");
        String token = memberToken(member);
        push(member, 1, "订单支付成功", "", "");
        push(member, 3, "签到有礼", "", "");

        JsonNode all = list(token, null);
        assertEquals(2, all.get("total").asInt());
        JsonNode marketing = list(token, 3);
        assertEquals(1, marketing.get("total").asInt());
        assertEquals("签到有礼", marketing.get("list").get(0).get("title").asText());
    }

    /** 后台只读台账：memberId/readStatus 筛选生效（admin 权限直通） */
    @Test
    void adminPageFilters() throws Exception {
        long member = createMember("消息台账");
        String token = memberToken(member);
        long unreadId = push(member, 1, "订单支付成功", "", "MO004");
        push(member, 3, "签到有礼", "", "");
        ok(post("/shop/notice/read/" + unreadId).header("Authorization", bearer(token)));

        String admin = adminToken();
        JsonNode all = adminPage(admin, "{\"memberId\":" + member + "}");
        assertEquals(2, all.get("total").asInt());
        JsonNode first = all.get("records").get(0);
        assertTrue(first.has("title") && first.has("readStatus") && first.has("createTime"), "台账行带完整字段");

        JsonNode readOnly = adminPage(admin, "{\"memberId\":" + member + ",\"readStatus\":1}");
        assertEquals(1, readOnly.get("total").asInt());
        assertEquals("订单支付成功", readOnly.get("records").get(0).get("title").asText());

        JsonNode marketing = adminPage(admin, "{\"memberId\":" + member + ",\"type\":3}");
        assertEquals(1, marketing.get("total").asInt());
    }

    /** 未登录查消息 → 401 */
    @Test
    void anonymousRejected() throws Exception {
        mvc.perform(get("/shop/notice/list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/shop/notice/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/shop/notice/read-all")).andExpect(status().isUnauthorized());
    }

    // ===== 小工具 =====

    /** 内部推送（/inner 免鉴权，成功返回原始载荷=消息ID） */
    private long push(long memberId, int type, String title, String content, String bizId) throws Exception {
        MvcResult result = mvc.perform(post("/inner/notice/push").characterEncoding("UTF-8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberId + ",\"type\":" + type + ",\"title\":\"" + title
                                + "\",\"content\":\"" + content + "\",\"bizId\":\"" + bizId + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return body(result).asLong();
    }

    private JsonNode list(String token, Integer type) throws Exception {
        String url = type == null ? "/shop/notice/list" : "/shop/notice/list?type=" + type;
        return ok(get(url).header("Authorization", bearer(token))).get("data");
    }

    private int unread(String token) throws Exception {
        return ok(get("/shop/notice/unread-count").header("Authorization", bearer(token)))
                .get("data").get("count").asInt();
    }

    private JsonNode adminPage(String adminToken, String jsonBody) throws Exception {
        return ok(post("/mall_notice/page").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(jsonBody)).get("data");
    }
}
