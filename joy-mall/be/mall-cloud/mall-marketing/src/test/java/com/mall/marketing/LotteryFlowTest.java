package com.mall.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.PointGrantDTO;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * V8 抽奖全链路回归：列表/权重随机/日限/限量降级/积分消耗/三类权益发放/后台 CRUD+prize-save。
 *
 * <p>跨服务调用全部 @MockBean（会员域积分、商品域 SKU、订单域建单），用例只依赖「本服务 + 测试库」。
 * 权重确定性：测试奖品只留一个正权重（weight=0 永不可中），保证断言无随机性。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LotteryFlowTest extends MallIntegrationTestBase {

    @MockBean
    private OrderInnerClient orderInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;
    @MockBean
    private MemberInnerClient memberInnerClient;

    private long memberA;
    private long memberB;
    private long addressA;
    private long goodsId;
    private long skuId;
    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> couponIds = new ArrayList<>();

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("抽奖A");
        memberB = createMember("抽奖B");
        addressA = createAddress(memberA);
        skuId = createGoodsWithSku("抽奖商品", 19900, 100);
        goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
    }

    @AfterAll
    void tearDown() {
        for (long id : activityIds) {
            jdbc.update("DELETE FROM mall_lottery_record WHERE activity_id = ?", id);
            jdbc.update("DELETE FROM mall_lottery_prize WHERE activity_id = ?", id);
            jdbc.update("DELETE FROM mall_lottery_activity WHERE id = ?", id);
        }
        for (long cid : couponIds) {
            jdbc.update("DELETE FROM mall_coupon_record WHERE coupon_id = ?", cid);
            jdbc.update("DELETE FROM mall_coupon WHERE id = ?", cid);
        }
        tearDownFixtures();
    }

    @BeforeEach
    void stubFeign() {
        lenient().when(memberInnerClient.grantPoints(any())).thenReturn(Map.of("points", 1000));
        GoodsBrief g = new GoodsBrief();
        g.setId(goodsId);
        g.setName("抽奖商品");
        g.setMainImage("/img/test.png");
        g.setStatus(1);
        lenient().when(goodsInnerClient.goodsByIds(anyList())).thenReturn(List.of(g));
        SkuBrief sku = new SkuBrief();
        sku.setId(skuId);
        sku.setGoodsId(goodsId);
        sku.setSpecJson("[]");
        sku.setImage("/img/test.png");
        lenient().when(goodsInnerClient.skuByGoods(any())).thenReturn(List.of(sku));
        lenient().when(orderInnerClient.createPromoOrder(any())).thenAnswer(inv -> {
            PromoOrderCreateDTO dto = inv.getArgument(0);
            OrderBrief b = new OrderBrief();
            b.setId(990003L);
            b.setOrderNo("MOCK-LOTTERY");
            b.setMemberId(dto.getMemberId());
            b.setStatus(10);
            b.setPayAmount(dto.getPrice());
            return b;
        });
    }

    /** 进行中活动才进 C 端列表，且带奖品池（共享库，按夹具 ID 断言进出） */
    @Test
    void activitiesListOnlyOngoing() throws Exception {
        long ongoing = createActivity("抽奖-进行中", 3, 0, "-1 DAY", "+1 DAY", 1);
        createPrize(ongoing, 3, "谢谢参与", null, 1, -1, 1);
        long notStarted = createActivity("抽奖-未开始", 3, 0, "+1 DAY", "+2 DAY", 1);
        long expired = createActivity("抽奖-已过期", 3, 0, "-2 DAY", "-1 DAY", 1);
        long closed = createActivity("抽奖-已结束", 3, 0, "-1 DAY", "+1 DAY", 2);

        JsonNode root = ok(mvc.perform(get("/shop/lottery/activities")
                .header("Authorization", bearer(memberToken(memberA)))));
        JsonNode list = root.get("data");
        assertTrue(list.isArray());
        Set<String> ids = new HashSet<>();
        for (JsonNode row : list) {
            ids.add(row.get("id").asText());
            if (String.valueOf(ongoing).equals(row.get("id").asText())) {
                assertEquals(3, row.get("dailyLimit").asInt());
                assertEquals(0, row.get("consumePoints").asInt());
                assertTrue(row.get("prizes").isArray());
                assertEquals("谢谢参与", row.get("prizes").get(0).get("name").asText());
            }
        }
        assertTrue(ids.contains(String.valueOf(ongoing)), "进行中活动必须在列表");
        assertFalse(ids.contains(String.valueOf(notStarted)), "未开始活动不应出现");
        assertFalse(ids.contains(String.valueOf(expired)), "已过期活动不应出现");
        assertFalse(ids.contains(String.valueOf(closed)), "已结束活动不应出现");
    }

    /** 免费抽奖：正权重奖品必中，积分奖励走会员域契约（changeType=6），落中奖记录 */
    @Test
    void freeDrawWinsPointsPrize() throws Exception {
        long activityId = createActivity("抽奖-积分场", 5, 0, "-1 DAY", "+1 DAY", 1);
        createPrize(activityId, 3, "谢谢参与", null, 0, -1, 9);   // 权重 0 永不可中
        long pointsPrize = createPrize(activityId, 0, "50积分", 50L, 10, -1, 1);

        JsonNode root = okJson("/shop/lottery/" + activityId + "/draw", memberToken(memberA), "{}");
        JsonNode data = root.get("data");
        assertEquals(1, data.get("isWin").asInt());
        assertEquals(0, data.get("prizeType").asInt());
        assertEquals("50积分", data.get("prizeName").asText());
        assertEquals(4, data.get("remain").asInt(), "dailyLimit=5 抽 1 次后应剩 4");

        ArgumentCaptor<PointGrantDTO> captor = ArgumentCaptor.forClass(PointGrantDTO.class);
        verify(memberInnerClient, times(1)).grantPoints(captor.capture());
        assertEquals(6, captor.getValue().getChangeType(), "中奖发放必须走 changeType=6");
        assertEquals(50, captor.getValue().getPoints(), "发放分值必须等于奖品 refId");
        assertEquals(memberA, captor.getValue().getMemberId());

        Long prizeId = jdbc.queryForObject(
                "SELECT prize_id FROM mall_lottery_record WHERE activity_id = ? AND member_id = ?",
                Long.class, activityId, memberA);
        assertEquals(pointsPrize, prizeId, "中奖记录必须落奖品ID");

        JsonNode my = ok(mvc.perform(get("/shop/lottery/my").header("Authorization", bearer(memberToken(memberA)))));
        boolean found = false;
        for (JsonNode row : my.get("data")) {
            if (String.valueOf(activityId).equals(row.get("activityId").asText())) {
                found = true;
                assertEquals(1, row.get("isWin").asInt());
                assertEquals("50积分", row.get("prizeName").asText());
            }
        }
        assertTrue(found, "我的抽奖记录必须包含本次中奖");
    }

    /** 日限：dailyLimit=1，第二次抽奖 400；remain 接口同步归零 */
    @Test
    void dailyLimitRejected() throws Exception {
        long activityId = createActivity("抽奖-日限场", 1, 0, "-1 DAY", "+1 DAY", 1);
        createPrize(activityId, 3, "谢谢参与", null, 1, -1, 1);
        String token = memberToken(memberB);

        JsonNode first = okJson("/shop/lottery/" + activityId + "/draw", token, "{}");
        assertEquals(0, first.get("data").get("isWin").asInt(), "首抽必出谢谢参与");
        bizErrorJson("/shop/lottery/" + activityId + "/draw", token, "{}", 400);

        JsonNode remain = ok(mvc.perform(get("/shop/lottery/my/remain?activityId=" + activityId)
                .header("Authorization", bearer(token))));
        assertEquals(0, remain.get("data").get("remain").asInt());
        assertEquals(1, remain.get("data").get("used").asInt());
    }

    /** 限量奖品：stock=1，第一人抽中后库存归零，第二人同池必降级谢谢参与 */
    @Test
    void limitedStockDegradesToFallback() throws Exception {
        long activityId = createActivity("抽奖-限量场", 5, 0, "-1 DAY", "+1 DAY", 1);
        long limited = createPrize(activityId, 0, "限量10积分", 10L, 10, 1, 1);
        createPrize(activityId, 3, "谢谢参与", null, 0, -1, 9);

        JsonNode win = okJson("/shop/lottery/" + activityId + "/draw", memberToken(memberA), "{}");
        assertEquals(1, win.get("data").get("isWin").asInt());
        assertEquals("限量10积分", win.get("data").get("prizeName").asText());
        Integer stock = jdbc.queryForObject(
                "SELECT stock FROM mall_lottery_prize WHERE id = ?", Integer.class, limited);
        assertEquals(0, stock, "抽中后限量库存必须归零");

        JsonNode degraded = okJson("/shop/lottery/" + activityId + "/draw", memberToken(memberB), "{}");
        assertEquals(0, degraded.get("data").get("isWin").asInt(), "限量抢光后必须降级");
        assertEquals(3, degraded.get("data").get("prizeType").asInt());
        assertEquals("谢谢参与", degraded.get("data").get("prizeName").asText());

        Integer stockAfter = jdbc.queryForObject(
                "SELECT stock FROM mall_lottery_prize WHERE id = ?", Integer.class, limited);
        assertEquals(0, stockAfter, "降级不得再扣库存（不出现负库存）");
    }

    /** 消耗积分活动：先扣参与积分（changeType=5，负数），未中奖不再发放 */
    @Test
    void consumePointsDeductedOnDraw() throws Exception {
        long activityId = createActivity("抽奖-积分消耗场", 3, 10, "-1 DAY", "+1 DAY", 1);
        createPrize(activityId, 3, "谢谢参与", null, 1, -1, 1);

        JsonNode root = okJson("/shop/lottery/" + activityId + "/draw", memberToken(memberA), "{}");
        assertEquals(0, root.get("data").get("isWin").asInt());
        assertEquals(2, root.get("data").get("remain").asInt());

        ArgumentCaptor<PointGrantDTO> captor = ArgumentCaptor.forClass(PointGrantDTO.class);
        verify(memberInnerClient, times(1)).grantPoints(captor.capture());
        assertEquals(5, captor.getValue().getChangeType(), "参与消耗必须走 changeType=5");
        assertEquals(-10, captor.getValue().getPoints(), "参与消耗必须为负数");
    }

    /** 优惠券奖品：本域落领券记录 + 券 received_count 条件 +1 */
    @Test
    void couponPrizeGranted() throws Exception {
        long couponId = createCoupon(5);
        long activityId = createActivity("抽奖-券场", 3, 0, "-1 DAY", "+1 DAY", 1);
        createPrize(activityId, 1, "满减券", couponId, 5, -1, 1);

        JsonNode root = okJson("/shop/lottery/" + activityId + "/draw", memberToken(memberA), "{}");
        assertEquals(1, root.get("data").get("isWin").asInt());
        assertEquals(1, root.get("data").get("prizeType").asInt());

        Integer received = jdbc.queryForObject(
                "SELECT received_count FROM mall_coupon WHERE id = ?", Integer.class, couponId);
        assertEquals(1, received, "中奖发券必须累加 received_count");
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_coupon_record WHERE coupon_id = ? AND member_id = ? AND remark = '抽奖发放'",
                Integer.class, couponId, memberA);
        assertEquals(1, cnt, "中奖发券必须落领券记录");
    }

    /** 实物奖品：无地址 400（无副作用）；带地址抽中 → 建 order_type=3 零元单并回填订单号 */
    @Test
    void physicalPrizeCreatesOrder() throws Exception {
        long activityId = createActivity("抽奖-实物场", 3, 0, "-1 DAY", "+1 DAY", 1);
        createPrize(activityId, 2, "实物大奖", goodsId, 5, -1, 1);
        String token = memberToken(memberA);

        bizErrorJson("/shop/lottery/" + activityId + "/draw", token, "{}", 400);

        JsonNode root = okJson("/shop/lottery/" + activityId + "/draw", token,
                json(Map.of("addressId", String.valueOf(addressA))));
        JsonNode data = root.get("data");
        assertEquals(1, data.get("isWin").asInt());
        assertEquals(2, data.get("prizeType").asInt());
        assertEquals("990003", data.get("orderId").asText());

        ArgumentCaptor<PromoOrderCreateDTO> captor = ArgumentCaptor.forClass(PromoOrderCreateDTO.class);
        verify(orderInnerClient).createPromoOrder(captor.capture());
        assertEquals(3, captor.getValue().getOrderType(), "抽奖实物单必须打 order_type=3");
        assertEquals(0L, captor.getValue().getPrice(), "实物奖品必须零元");
        assertEquals(skuId, captor.getValue().getSkuId(), "必须按商品解析出默认 SKU");
        assertEquals(goodsId, captor.getValue().getGoodsId());

        Long orderId = jdbc.queryForObject(
                "SELECT order_id FROM mall_lottery_record WHERE activity_id = ? AND member_id = ?",
                Long.class, activityId, memberA);
        assertEquals(990003L, orderId, "中奖记录必须回填订单ID");
        Integer receiveStatus = jdbc.queryForObject(
                "SELECT receive_status FROM mall_lottery_record WHERE activity_id = ? AND member_id = ?",
                Integer.class, activityId, memberA);
        assertEquals(1, receiveStatus);
    }

    /** 后台：新增 → prize-save 整池保存 → 详情含奖品 → 修改 → 删除（连带清池） */
    @Test
    void adminCrudAndPrizeSave() throws Exception {
        String admin = adminToken();
        JsonNode add = okJson("/mall_lottery", admin,
                "{\"name\":\"后台抽奖\",\"dailyLimit\":2,\"consumePoints\":0,"
                        + "\"startTime\":\"2026-01-01 00:00:00\",\"endTime\":\"2027-01-01 00:00:00\",\"status\":1}");
        assertEquals(200, add.get("code").asInt());
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_lottery_activity WHERE name = '后台抽奖' ORDER BY id DESC LIMIT 1", Long.class);
        activityIds.add(id);

        JsonNode saved = okJson("/mall_lottery/prize-save", admin,
                "{\"activityId\":" + id + ",\"prizes\":["
                        + "{\"prizeType\":0,\"name\":\"积分50\",\"refId\":50,\"weight\":10,\"stock\":-1,\"sort\":1},"
                        + "{\"prizeType\":3,\"name\":\"谢谢参与\",\"weight\":1,\"sort\":9}]}");
        assertEquals(200, saved.get("code").asInt());

        JsonNode detail = ok(mvc.perform(get("/mall_lottery/" + id)
                .header("Authorization", bearer(admin))));
        assertEquals("后台抽奖", detail.get("data").get("activity").get("name").asText());
        assertEquals(2, detail.get("data").get("prizes").size());
        assertEquals("积分50", detail.get("data").get("prizes").get(0).get("name").asText());
        assertEquals(-1, detail.get("data").get("prizes").get(1).get("stock").asInt(), "缺省库存应为 -1 不限量");

        JsonNode edited = ok(mvc.perform(put("/mall_lottery")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + id + ",\"name\":\"后台抽奖V2\",\"dailyLimit\":3,"
                        + "\"startTime\":\"2026-01-01 00:00:00\",\"endTime\":\"2027-01-01 00:00:00\",\"status\":1}")));
        assertEquals("后台抽奖V2", jdbc.queryForObject(
                "SELECT name FROM mall_lottery_activity WHERE id = ?", String.class, id));
        assertEquals(3, jdbc.queryForObject(
                "SELECT daily_limit FROM mall_lottery_activity WHERE id = ?", Integer.class, id));

        JsonNode page = okJson("/mall_lottery/page", admin, "{\"pageNum\":1,\"pageSize\":50}");
        boolean found = false;
        for (JsonNode row : page.get("data").get("records")) {
            if (String.valueOf(id).equals(row.get("id").asText())) {
                found = true;
                assertEquals(2, row.get("prizeCount").asInt());
            }
        }
        assertTrue(found, "后台分页必须能查到新活动且带奖品数");

        ok(mvc.perform(delete("/mall_lottery/" + id)
                .header("Authorization", bearer(admin))));
        bizError(mvc.perform(get("/mall_lottery/" + id)
                .header("Authorization", bearer(admin))), 400);
        Integer prizeLeft = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_lottery_prize WHERE activity_id = ? AND deleted = 0",
                Integer.class, id);
        assertEquals(0, prizeLeft, "删除活动必须连带逻辑删除奖品池");
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

    private long createActivity(String name, int dailyLimit, int consumePoints,
                                String startShift, String endShift, int status) {
        String unique = name + "-" + System.nanoTime();
        jdbc.update("INSERT INTO mall_lottery_activity (name, daily_limit, consume_points,"
                        + " start_time, end_time, status) VALUES (?,?,?,"
                        + " NOW() + INTERVAL " + startShift + ", NOW() + INTERVAL " + endShift + ", ?)",
                unique, dailyLimit, consumePoints, status);
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_lottery_activity WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class, unique);
        activityIds.add(id);
        return id;
    }

    private long createPrize(long activityId, int prizeType, String name, Long refId,
                             int weight, int stock, int sort) {
        jdbc.update("INSERT INTO mall_lottery_prize (activity_id, prize_type, name, ref_id, weight, stock, sort)"
                        + " VALUES (?,?,?,?,?,?,?)",
                activityId, prizeType, name, refId, weight, stock, sort);
        return jdbc.queryForObject(
                "SELECT id FROM mall_lottery_prize WHERE activity_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
                Long.class, activityId, name);
    }

    private long createCoupon(int totalCount) {
        jdbc.update("INSERT INTO mall_coupon (name, type, amount, threshold_amount, total_count,"
                        + " received_count, per_limit, start_time, end_time, status)"
                        + " VALUES ('抽奖券', 1, 500, 0, ?, 0, 10,"
                        + " NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY, 1)", totalCount);
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_coupon WHERE name = '抽奖券' ORDER BY id DESC LIMIT 1", Long.class);
        couponIds.add(id);
        return id;
    }
}
