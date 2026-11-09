package com.mall.member;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V6 T-01 会员足迹回归（原单体 FootprintTest 改写）。
 *
 * <p>足迹的唯一键是 {@code uk_member_goods(member_id, goods_id)}，重复浏览只刷新时间不新增行；
 * 清空是逻辑删，清空后再浏览同一商品必须「复活」原行而不是插新行（否则撞唯一键 500）。
 * 身份只认 token，列表/清空一律按会员隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FootprintTest extends MallIntegrationTestBase {

    private long memberA;
    private long memberB;
    private String tokenA;
    private String tokenB;
    private long goods1;
    private long goods2;

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("足迹A");
        memberB = createMember("足迹B");
        tokenA = memberToken(memberA);
        tokenB = memberToken(memberB);
        long categoryId = createCategory();
        goods1 = createGoods(categoryId, "足迹商品一", 100L);
        goods2 = createGoods(categoryId, "足迹商品二", 200L);
    }

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    /** 同一商品重复浏览只留一行，且最近浏览排在最前 */
    @Test
    void recordDedupesAndSortsByViewTime() throws Exception {
        record(tokenA, goods1);
        record(tokenA, goods2);
        assertEquals(2, footprintTotal(tokenA), "两件商品两条足迹");

        Thread.sleep(1100); // view_time 精度到秒，间隔一下才能验证排序
        record(tokenA, goods1);

        JsonNode data = footprintList(tokenA);
        assertEquals(2, data.get("total").asInt(), "重复浏览不得新增行");
        JsonNode first = data.get("list").get(0);
        assertEquals(goods1, first.get("goodsId").asLong(), "最近浏览的排最前");
        assertEquals("足迹商品一", first.get("goodsName").asText(), "列表带商品名");
        assertEquals(100L, first.get("priceMin").asLong(), "列表带价格");
    }

    /** 清空是逻辑删：清空后再浏览同一商品要复活原行，不撞唯一键 */
    @Test
    void clearThenRecordAgainRevivesRow() throws Exception {
        long member = createMember("足迹清空");
        String token = memberToken(member);
        record(token, goods1);
        record(token, goods2);

        JsonNode cleared = ok(post("/shop/footprint/clear").header("Authorization", bearer(token)));
        assertEquals(2, cleared.get("data").get("cleared").asInt(), "清空 2 条");
        assertEquals(0, footprintTotal(token), "清空后列表为空");

        ok(post("/shop/footprint/record").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"goodsId\":\"" + goods1 + "\"}"));
        assertEquals(1, footprintTotal(token), "复活原行而不是插新行");
    }

    /** 会员之间不串数据：B 的足迹 A 看不到，A 清空不影响 B */
    @Test
    void footprintsAreMemberScoped() throws Exception {
        long a = createMember("足迹隔离A");
        long b = createMember("足迹隔离B");
        String ta = memberToken(a);
        String tb = memberToken(b);
        record(ta, goods1);
        record(tb, goods2);
        record(tb, goods1);

        assertEquals(1, footprintTotal(ta));
        assertEquals(2, footprintTotal(tb));

        ok(post("/shop/footprint/clear").header("Authorization", bearer(ta)));
        assertEquals(0, footprintTotal(ta));
        assertEquals(2, footprintTotal(tb), "A 清空不得动 B 的足迹");
    }

    /** 商品已删除/不存在：静默成功，不写脏行 */
    @Test
    void recordUnknownGoodsIsSilent() throws Exception {
        long member = createMember("足迹脏数据");
        String token = memberToken(member);

        ok(post("/shop/footprint/record").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"goodsId\":\"99999999\"}"));
        assertEquals(0, footprintTotal(token), "不存在的商品不落足迹");
    }

    /** 未登录访问足迹 → 401（足迹是会员私有数据） */
    @Test
    void anonymousForbidden() throws Exception {
        mvc.perform(get("/shop/footprint/list")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    // ===== 小工具 =====

    private void record(String token, long goodsId) throws Exception {
        ok(post("/shop/footprint/record").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"goodsId\":\"" + goodsId + "\"}"));
    }

    private JsonNode footprintList(String token) throws Exception {
        return ok(get("/shop/footprint/list").header("Authorization", bearer(token))).get("data");
    }

    private int footprintTotal(String token) throws Exception {
        return footprintList(token).get("total").asInt();
    }
}
