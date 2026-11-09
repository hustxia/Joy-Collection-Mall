package com.mall.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.PromoOrderCreateDTO;
import com.mall.common.feign.dto.SkuBrief;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * V8 拼团全链路回归：列表/开团/参团/成团状态机/重复参团/过期团/满员团/后台 page+team-page。
 *
 * <p>跨服务调用全部 @MockBean（商品域取快照、订单域建单），用例只依赖「本服务 + 测试库」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupFlowTest extends MallIntegrationTestBase {

    @MockBean
    private OrderInnerClient orderInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;
    @MockBean
    private MemberInnerClient memberInnerClient;

    private long memberA;
    private long memberB;
    private long memberC;
    private long addressA;
    private long addressB;
    private long addressC;
    private long goodsId;
    private long skuId;
    private final List<Long> activityIds = new ArrayList<>();

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("拼团A");
        memberB = createMember("拼团B");
        memberC = createMember("拼团C");
        addressA = createAddress(memberA);
        addressB = createAddress(memberB);
        addressC = createAddress(memberC);
        skuId = createGoodsWithSku("拼团商品", 269900, 100);
        goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
    }

    @AfterAll
    void tearDown() {
        for (long id : activityIds) {
            jdbc.update("DELETE FROM mall_group_member WHERE team_id IN "
                    + "(SELECT id FROM mall_group_team WHERE activity_id = ?)", id);
            jdbc.update("DELETE FROM mall_group_team WHERE activity_id = ?", id);
            jdbc.update("DELETE FROM mall_group_activity WHERE id = ?", id);
        }
        tearDownFixtures();
    }

    @BeforeEach
    void stubFeign() {
        GoodsBrief g = new GoodsBrief();
        g.setId(goodsId);
        g.setName("拼团商品");
        g.setMainImage("/img/test.png");
        g.setStatus(1);
        lenient().when(goodsInnerClient.goodsByIds(anyList())).thenReturn(List.of(g));
        SkuBrief sku = new SkuBrief();
        sku.setId(skuId);
        sku.setGoodsId(goodsId);
        sku.setSpecJson("[]");
        sku.setImage("/img/test.png");
        lenient().when(goodsInnerClient.skuByIds(anyList())).thenReturn(List.of(sku));
        lenient().when(orderInnerClient.createPromoOrder(any())).thenAnswer(inv -> {
            PromoOrderCreateDTO dto = inv.getArgument(0);
            OrderBrief b = new OrderBrief();
            b.setId(990002L);
            b.setOrderNo("MOCK-GROUP");
            b.setMemberId(dto.getMemberId());
            b.setStatus(10);
            b.setPayAmount(dto.getPrice());
            return b;
        });
    }

    /** 进行中活动才进 C 端列表，且带商品名/图（共享库，按夹具 ID 断言进出） */
    @Test
    void activitiesListOnlyOngoing() throws Exception {
        long ongoing = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 1);
        long notStarted = createActivity(skuId, 2, 24, "+1 DAY", "+2 DAY", 1);   // 未开始
        long expired = createActivity(skuId, 2, 24, "-2 DAY", "-1 DAY", 1);     // 已过期
        long closed = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 2);      // 已结束

        JsonNode root = ok(mvc.perform(get("/shop/group/activities")
                .header("Authorization", bearer(memberToken(memberA)))));
        JsonNode list = root.get("data");
        assertTrue(list.isArray());
        Set<String> ids = new HashSet<>();
        for (JsonNode row : list) {
            ids.add(row.get("id").asText());
            assertEquals("拼团商品", row.get("goodsName").asText(), "商品名应经商品域补齐");
            assertEquals(239900L, row.get("groupPrice").asLong());
            assertEquals(2, row.get("groupSize").asInt());
        }
        assertTrue(ids.contains(String.valueOf(ongoing)), "进行中活动必须在列表");
        assertFalse(ids.contains(String.valueOf(notStarted)), "未开始活动不应出现");
        assertFalse(ids.contains(String.valueOf(expired)), "已过期活动不应出现");
        assertFalse(ids.contains(String.valueOf(closed)), "已结束活动不应出现");
    }

    /** 开团成功：团（0拼团中/1人）+ 团长成员行 + 订单域收到 order_type=2 拼团价 + 回填订单号 */
    @Test
    void openTeamSuccess() throws Exception {
        long activityId = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 1);
        String token = memberToken(memberA);

        JsonNode root = okJson("/shop/group/" + activityId + "/open", token,
                json(java.util.Map.of("addressId", String.valueOf(addressA))));
        JsonNode data = root.get("data");
        assertEquals("990002", data.get("orderId").asText());
        assertEquals(239900L, data.get("payAmount").asLong());
        assertEquals(0, data.get("teamStatus").asInt(), "开团后应为拼团中");
        assertEquals(1, data.get("currentSize").asInt());
        assertNotNull(data.get("expireTime"), "开团必须给出成团截止时间");
        long teamId = Long.parseLong(data.get("teamId").asText());

        assertEquals(0, teamStatusOf(teamId));
        assertEquals(1, teamSizeOf(teamId));
        Long orderId = jdbc.queryForObject(
                "SELECT order_id FROM mall_group_member WHERE team_id = ? AND member_id = ? AND is_leader = 1",
                Long.class, teamId, memberA);
        assertEquals(990002L, orderId, "团长成员行必须回填订单ID");

        ArgumentCaptor<PromoOrderCreateDTO> captor = ArgumentCaptor.forClass(PromoOrderCreateDTO.class);
        verify(orderInnerClient).createPromoOrder(captor.capture());
        assertEquals(2, captor.getValue().getOrderType(), "拼团单必须打 order_type=2");
        assertEquals(239900L, captor.getValue().getPrice(), "成交价必须是拼团价");
        assertEquals(memberA, captor.getValue().getMemberId());

        // 我的拼团里能看到这个团（团长、拼团中）
        JsonNode my = ok(mvc.perform(get("/shop/group/my").header("Authorization", bearer(token))));
        boolean found = false;
        for (JsonNode row : my.get("data")) {
            if (String.valueOf(teamId).equals(row.get("teamId").asText())) {
                found = true;
                assertEquals(1, row.get("isLeader").asInt());
                assertEquals(0, row.get("teamStatus").asInt());
                assertEquals("拼团商品", row.get("goodsName").asText());
            }
        }
        assertTrue(found, "我的拼团必须包含刚开的团");
    }

    /** 参团成功并成团：2 人团，A 开团 B 参团 → 满员置 status=1 */
    @Test
    void joinCompletesTeam() throws Exception {
        long activityId = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 1);
        JsonNode opened = okJson("/shop/group/" + activityId + "/open", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA)))).get("data");
        String teamId = opened.get("teamId").asText();

        JsonNode joined = okJson("/shop/group/team/" + teamId + "/join", memberToken(memberB),
                json(java.util.Map.of("addressId", String.valueOf(addressB)))).get("data");
        assertEquals(1, joined.get("teamStatus").asInt(), "满员后必须成团");
        assertEquals(2, joined.get("currentSize").asInt());

        assertEquals(1, teamStatusOf(Long.parseLong(teamId)), "DB 团状态必须为已成团");
        assertEquals(2, teamSizeOf(Long.parseLong(teamId)));
        Integer orderRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_group_member WHERE team_id = ? AND order_id IS NOT NULL",
                Integer.class, Long.parseLong(teamId));
        assertEquals(2, orderRows, "团长+团员都必须有订单");
        verify(orderInnerClient, times(2)).createPromoOrder(any());
    }

    /** 重复参团：A 开 A 再进 → 400，人数不涨 */
    @Test
    void joinDuplicateRejected() throws Exception {
        long activityId = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 1);
        String token = memberToken(memberA);
        JsonNode opened = okJson("/shop/group/" + activityId + "/open", token,
                json(java.util.Map.of("addressId", String.valueOf(addressA)))).get("data");
        String teamId = opened.get("teamId").asText();

        bizErrorJson("/shop/group/team/" + teamId + "/join", token,
                json(java.util.Map.of("addressId", String.valueOf(addressA))), 400);
        assertEquals(1, teamSizeOf(Long.parseLong(teamId)), "重复参团不得增加人数");
        assertEquals(0, teamStatusOf(Long.parseLong(teamId)));
    }

    /** 过期团：expire_time 已过且 status=0 → 参团 400，并懒加载置为失败 */
    @Test
    void joinExpiredTeamRejected() throws Exception {
        long activityId = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 1);
        JsonNode opened = okJson("/shop/group/" + activityId + "/open", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA)))).get("data");
        long teamId = Long.parseLong(opened.get("teamId").asText());
        jdbc.update("UPDATE mall_group_team SET expire_time = NOW() - INTERVAL 1 HOUR WHERE id = ?", teamId);

        bizErrorJson("/shop/group/team/" + teamId + "/join", memberToken(memberB),
                json(java.util.Map.of("addressId", String.valueOf(addressB))), 400);
        assertEquals(2, teamStatusOf(teamId), "过期团必须被懒加载置为已失败");
        assertEquals(1, teamSizeOf(teamId), "过期团不得增加人数");
    }

    /** 满员团：2 人团满后第三人参团 → 400 */
    @Test
    void joinFullTeamRejected() throws Exception {
        long activityId = createActivity(skuId, 2, 24, "-1 DAY", "+1 DAY", 1);
        JsonNode opened = okJson("/shop/group/" + activityId + "/open", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA)))).get("data");
        String teamId = opened.get("teamId").asText();
        okJson("/shop/group/team/" + teamId + "/join", memberToken(memberB),
                json(java.util.Map.of("addressId", String.valueOf(addressB))));

        bizErrorJson("/shop/group/team/" + teamId + "/join", memberToken(memberC),
                json(java.util.Map.of("addressId", String.valueOf(addressC))), 400);
        assertEquals(2, teamSizeOf(Long.parseLong(teamId)), "满员团人数不得再涨");
        assertEquals(1, teamStatusOf(Long.parseLong(teamId)));
    }

    /** 时间窗之外开团 → 400 */
    @Test
    void openRejectsOutOfWindow() throws Exception {
        long notStarted = createActivity(skuId, 2, 24, "+1 DAY", "+2 DAY", 1);
        bizErrorJson("/shop/group/" + notStarted + "/open", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA))), 400);
    }

    /** 后台：新增活动 → 分页可见（含商品名）→ 修改 → C 端开团 → team-page 台账可见 */
    @Test
    void adminCrudAndTeamPage() throws Exception {
        String admin = adminToken();
        String body = """
                {"goodsId":"%d","skuId":"%d","originPrice":269900,"groupPrice":239900,
                 "groupSize":3,"validHours":12,"startTime":"2020-01-01 00:00:00","endTime":"2099-01-01 00:00:00","status":1}
                """.formatted(goodsId, skuId);
        ok(mvc.perform(post("/mall_group").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)));
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_group_activity WHERE sku_id = ? AND valid_hours = 12 ORDER BY id DESC LIMIT 1",
                Long.class, skuId);
        assertNotNull(id);
        activityIds.add(id);

        JsonNode page = ok(mvc.perform(post("/mall_group/page").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"pageNum\":1,\"pageSize\":50}")));
        boolean found = false;
        for (JsonNode row : page.get("data").get("records")) {
            if (String.valueOf(id).equals(row.get("id").asText())) {
                found = true;
                assertEquals("拼团商品", row.get("goodsName").asText());
                assertEquals(3, row.get("groupSize").asInt());
            }
        }
        assertTrue(found, "后台分页必须能查到新活动");

        ok(mvc.perform(put("/mall_group").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        body.replace("\"validHours\":12", "\"validHours\":48")
                                .replaceFirst("\\{", "{\"id\":\"" + id + "\","))));
        Integer validHours = jdbc.queryForObject(
                "SELECT valid_hours FROM mall_group_activity WHERE id = ?", Integer.class, id);
        assertEquals(48, validHours);

        JsonNode opened = okJson("/shop/group/" + id + "/open", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA)))).get("data");
        String teamId = opened.get("teamId").asText();

        JsonNode teamPage = ok(mvc.perform(post("/mall_group/team-page").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"pageNum\":1,\"pageSize\":50,\"activityId\":\"" + id + "\"}")));
        boolean teamFound = false;
        for (JsonNode row : teamPage.get("data").get("records")) {
            if (teamId.equals(row.get("id").asText())) {
                teamFound = true;
                assertEquals(1, row.get("currentSize").asInt());
                assertEquals(3, row.get("groupSize").asInt());
                assertEquals(0, row.get("status").asInt());
            }
        }
        assertTrue(teamFound, "团台账必须能查到刚开的团");
    }

    // ===== 小工具 =====

    /** postJson 内部已 perform，直接断言业务 code=200 并返回响应体 */
    private JsonNode okJson(String url, String token, String body) throws Exception {
        JsonNode root = body(postJson(url, token, body));
        assertEquals(200, root.get("code").asInt(), "期望业务 code=200，实际：" + root);
        return root;
    }

    private JsonNode bizErrorJson(String url, String token, String body, int expectedCode) throws Exception {
        JsonNode root = body(postJson(url, token, body));
        assertEquals(expectedCode, root.get("code").asInt(), "期望业务 code=" + expectedCode + "，实际：" + root);
        return root;
    }

    private long createActivity(long sku, int groupSize, int validHours, String startShift, String endShift, int status) {
        jdbc.update("INSERT INTO mall_group_activity (goods_id, sku_id, origin_price, group_price, group_size,"
                        + " valid_hours, start_time, end_time, status) VALUES (?,?,269900,239900,?,?,"
                        + " NOW() + INTERVAL " + startShift + ", NOW() + INTERVAL " + endShift + ", ?)",
                goodsId, sku, groupSize, validHours, status);
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_group_activity WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, sku);
        activityIds.add(id);
        return id;
    }

    private int teamStatusOf(long teamId) {
        Integer v = jdbc.queryForObject("SELECT status FROM mall_group_team WHERE id = ?", Integer.class, teamId);
        return v == null ? -1 : v;
    }

    private int teamSizeOf(long teamId) {
        Integer v = jdbc.queryForObject("SELECT current_size FROM mall_group_team WHERE id = ?", Integer.class, teamId);
        return v == null ? -1 : v;
    }
}
