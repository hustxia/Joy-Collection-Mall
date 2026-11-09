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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * V8 秒杀全链路回归：列表/详情/抢购（Redis 不可用时降级 DB 乐观锁）/限购/售罄/回滚/后台 CRUD。
 *
 * <p>跨服务调用全部 @MockBean（商品域取快照、订单域建单），用例只依赖「本服务 + 测试库」。
 * 本机无 Redis 时 tryDeduct 走 DEGRADED 分支，恰好覆盖降级路径；Redis 预扣正路径由冒烟覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeckillFlowTest extends MallIntegrationTestBase {

    @MockBean
    private OrderInnerClient orderInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;
    @MockBean
    private MemberInnerClient memberInnerClient;

    private long memberA;
    private long addressA;
    private long goodsId;
    private long skuId;
    private final List<Long> activityIds = new ArrayList<>();

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("秒杀A");
        addressA = createAddress(memberA);
        skuId = createGoodsWithSku("秒杀商品", 399900, 100);
        goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
    }

    @AfterAll
    void tearDown() {
        for (long id : activityIds) {
            jdbc.update("DELETE FROM mall_seckill_record WHERE activity_id = ?", id);
            jdbc.update("DELETE FROM mall_seckill_activity WHERE id = ?", id);
        }
        tearDownFixtures();
    }

    @BeforeEach
    void stubFeign() {
        GoodsBrief g = new GoodsBrief();
        g.setId(goodsId);
        g.setName("秒杀商品");
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
            b.setId(990001L);
            b.setOrderNo("MOCK-SECKILL");
            b.setMemberId(dto.getMemberId());
            b.setStatus(10);
            b.setPayAmount(dto.getPrice());
            return b;
        });
    }

    /** 进行中活动才进 C 端列表，且带商品名/图与我的已购数（共享库，按夹具 ID 断言进出） */
    @Test
    void activitiesListOnlyOnSale() throws Exception {
        long ongoing = createActivity(skuId, 10, 1, "-1 DAY", "+1 DAY", 1);
        long notStarted = createActivity(skuId, 10, 1, "+1 DAY", "+2 DAY", 1);   // 未开始
        long expired = createActivity(skuId, 10, 1, "-2 DAY", "-1 DAY", 1);     // 已过期
        long closed = createActivity(skuId, 10, 1, "-1 DAY", "+1 DAY", 2);      // 已结束

        JsonNode root = ok(mvc.perform(get("/shop/seckill/activities")
                .header("Authorization", bearer(memberToken(memberA)))));
        JsonNode list = root.get("data");
        assertTrue(list.isArray());
        Set<String> ids = new HashSet<>();
        for (JsonNode row : list) {
            ids.add(row.get("id").asText());
            assertEquals("秒杀商品", row.get("goodsName").asText(), "商品名应经商品域补齐");
            assertEquals(299900L, row.get("seckillPrice").asLong());
        }
        assertTrue(ids.contains(String.valueOf(ongoing)), "进行中活动必须在列表");
        assertFalse(ids.contains(String.valueOf(notStarted)), "未开始活动不应出现");
        assertFalse(ids.contains(String.valueOf(expired)), "已过期活动不应出现");
        assertFalse(ids.contains(String.valueOf(closed)), "已结束活动不应出现");
    }

    /** 抢购成功：DB 库存 -1、参与记录带订单号、订单域收到 order_type=1 秒杀价 */
    @Test
    void buySuccess() throws Exception {
        long activityId = createActivity(skuId, 5, 1, "-1 DAY", "+1 DAY", 1);
        String token = memberToken(memberA);

        JsonNode root = okJson("/shop/seckill/" + activityId + "/buy", token,
                json(java.util.Map.of("addressId", String.valueOf(addressA))));
        assertEquals("990001", root.get("data").get("orderId").asText());
        assertEquals(299900L, root.get("data").get("payAmount").asLong());

        assertEquals(4, availableStockOf(activityId), "DB 乐观锁必须真实扣减");
        Long orderId = jdbc.queryForObject(
                "SELECT order_id FROM mall_seckill_record WHERE activity_id = ? AND member_id = ?",
                Long.class, activityId, memberA);
        assertEquals(990001L, orderId, "参与记录必须回填订单ID");

        ArgumentCaptor<PromoOrderCreateDTO> captor = ArgumentCaptor.forClass(PromoOrderCreateDTO.class);
        verify(orderInnerClient).createPromoOrder(captor.capture());
        assertEquals(1, captor.getValue().getOrderType(), "秒杀单必须打 order_type=1");
        assertEquals(299900L, captor.getValue().getPrice(), "成交价必须是秒杀价");
        assertEquals(memberA, captor.getValue().getMemberId());

        // 详情里我的已购数 = 1
        JsonNode detail = ok(mvc.perform(get("/shop/seckill/" + activityId)
                .header("Authorization", bearer(token))));
        assertEquals(1, detail.get("data").get("myCount").asInt());
    }

    /** 未开始/已结束活动抢购 → 400 */
    @Test
    void buyRejectsOutOfWindow() throws Exception {
        long notStarted = createActivity(skuId, 5, 1, "+1 DAY", "+2 DAY", 1);
        bizErrorJson("/shop/seckill/" + notStarted + "/buy", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA))), 400);
        assertEquals(5, availableStockOf(notStarted), "失败不得扣库存");
    }

    /** 限购：per_limit=1 买第二单 → 400，且库存不回退第一单 */
    @Test
    void buyEnforcesPerLimit() throws Exception {
        long activityId = createActivity(skuId, 5, 1, "-1 DAY", "+1 DAY", 1);
        String token = memberToken(memberA);
        okJson("/shop/seckill/" + activityId + "/buy", token,
                json(java.util.Map.of("addressId", String.valueOf(addressA))));
        assertEquals(4, availableStockOf(activityId));

        bizErrorJson("/shop/seckill/" + activityId + "/buy", token,
                json(java.util.Map.of("addressId", String.valueOf(addressA))), 400);
        assertEquals(4, availableStockOf(activityId), "限购拦截发生在扣库存之前");
    }

    /** 售罄：available_stock=0 → 400（DB 乐观锁兜底，Redis 有无都成立） */
    @Test
    void buySoldOut() throws Exception {
        long activityId = createActivity(skuId, 5, 1, "-1 DAY", "+1 DAY", 1);
        jdbc.update("UPDATE mall_seckill_activity SET available_stock = 0 WHERE id = ?", activityId);
        bizErrorJson("/shop/seckill/" + activityId + "/buy", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA))), 400);
        assertEquals(0, availableStockOf(activityId));
    }

    /** 订单域建单失败 → 事务回滚：库存恢复、无参与记录 */
    @Test
    void buyRollsBackWhenOrderFails() throws Exception {
        long activityId = createActivity(skuId, 5, 1, "-1 DAY", "+1 DAY", 1);
        // doThrow 风格重打桩：when(...).thenThrow(...) 会先以 null 入参执行旧 Answer 导致 NPE
        lenient().doThrow(new RuntimeException("boom")).when(orderInnerClient).createPromoOrder(any());

        bizErrorJson("/shop/seckill/" + activityId + "/buy", memberToken(memberA),
                json(java.util.Map.of("addressId", String.valueOf(addressA))), 500);
        assertEquals(5, availableStockOf(activityId), "建单失败必须回滚 DB 库存");
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_seckill_record WHERE activity_id = ? AND member_id = ?",
                Integer.class, activityId, memberA);
        assertEquals(0, cnt, "建单失败不得残留参与记录");
    }

    /** 后台 CRUD：新增（剩余=总量）→ 分页可见 → 改总量 → 结束后 C 端不可见 */
    @Test
    void adminCrudFlow() throws Exception {
        String admin = adminToken();
        String body = """
                {"goodsId":"%d","skuId":"%d","originPrice":399900,"seckillPrice":299900,
                 "totalStock":30,"perLimit":1,"startTime":"2020-01-01 00:00:00","endTime":"2099-01-01 00:00:00","status":1}
                """.formatted(goodsId, skuId);
        ok(mvc.perform(post("/mall_seckill").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)));
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_seckill_activity WHERE sku_id = ? AND total_stock = 30 ORDER BY id DESC LIMIT 1",
                Long.class, skuId);
        assertNotNull(id);
        activityIds.add(id);
        assertEquals(30, availableStockOf(id), "新增时剩余库存 = 总库存");

        JsonNode page = ok(mvc.perform(post("/mall_seckill/page").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"pageNum\":1,\"pageSize\":50}")));
        boolean found = false;
        for (JsonNode row : page.get("data").get("records")) {
            if (String.valueOf(id).equals(row.get("id").asText())) {
                found = true;
                assertEquals("秒杀商品", row.get("goodsName").asText());
            }
        }
        assertTrue(found, "后台分页必须能查到新活动");

        ok(mvc.perform(put("/mall_seckill").header("Authorization", bearer(admin))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        body.replace("\"totalStock\":30", "\"totalStock\":50").replaceFirst("\\{", "{\"id\":\"" + id + "\","))));
        assertEquals(50, availableStockOf(id), "改总量后剩余库存 = 新总量 - 已售(0)");

        ok(mvc.perform(post("/mall_seckill/close/" + id).header("Authorization", bearer(admin))));
        Integer status = jdbc.queryForObject("SELECT status FROM mall_seckill_activity WHERE id = ?",
                Integer.class, id);
        assertEquals(2, status);

        JsonNode list = ok(mvc.perform(get("/shop/seckill/activities")
                .header("Authorization", bearer(memberToken(memberA))))).get("data");
        for (JsonNode row : list) {
            assertTrue(!String.valueOf(id).equals(row.get("id").asText()), "已结束活动不得出现在 C 端");
        }
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

    private long createActivity(long sku, int stock, int perLimit, String startShift, String endShift, int status) {
        jdbc.update("INSERT INTO mall_seckill_activity (goods_id, sku_id, origin_price, seckill_price, total_stock,"
                        + " available_stock, per_limit, start_time, end_time, status) VALUES (?,?,399900,299900,?,?,?,"
                        + " NOW() + INTERVAL " + startShift + ", NOW() + INTERVAL " + endShift + ", ?)",
                goodsId, sku, stock, stock, perLimit, status);
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_seckill_activity WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, sku);
        activityIds.add(id);
        return id;
    }

    private int availableStockOf(long activityId) {
        Integer v = jdbc.queryForObject("SELECT available_stock FROM mall_seckill_activity WHERE id = ?",
                Integer.class, activityId);
        return v == null ? -1 : v;
    }
}
