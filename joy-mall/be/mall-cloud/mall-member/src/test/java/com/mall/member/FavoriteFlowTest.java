package com.mall.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 收藏回归。
 *
 * <p>核心是切换口径：uk_favorite_member_goods(member_id, goods_id, deleted) 含 deleted 列，
 * 「取消收藏→再收藏」必须恢复原行而不是插新行（否则第三次切换撞唯一键 500），
 * 且全程同会员同商品只留一行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FavoriteFlowTest extends MallIntegrationTestBase {

    @MockBean
    private GoodsInnerClient goodsInnerClient;

    private long goods1;
    private long goods2;

    @BeforeAll
    void prepareFixtures() {
        long categoryId = createCategory();
        goods1 = createGoods(categoryId, "收藏商品一", 100L);
        goods2 = createGoods(categoryId, "收藏商品二", 200L);
    }

    @AfterAll
    void tearDown() {
        jdbc.update("DELETE FROM mall_favorite WHERE member_id IN"
                + " (SELECT id FROM mall_member WHERE nickname LIKE '" + TEST_MARK + "%')");
        tearDownFixtures();
    }

    @BeforeEach
    void stubGoods() {
        when(goodsInnerClient.goodsByIds(anyList())).thenReturn(List.of(
                goods(goods1, "收藏商品一", 100L), goods(goods2, "收藏商品二", 200L)));
    }

    /** 切换幂等：收藏→取消→再收藏，favorited 依次为 true/false/true，且始终只有一行 */
    @Test
    void toggleIsIdempotentAndRestoresRow() throws Exception {
        long member = createMember("收藏切换");
        String token = memberToken(member);

        assertTrue(toggle(token, goods1), "首次收藏");
        assertTrue(check(token, goods1));
        assertFalse(toggle(token, goods1), "再次调用=取消收藏");
        assertFalse(check(token, goods1));
        assertTrue(toggle(token, goods1), "第三次=恢复收藏");
        assertTrue(check(token, goods1));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_favorite WHERE member_id = ? AND goods_id = ?",
                Integer.class, member, goods1), "全程只留一行，恢复而不是插新行");
        assertEquals(0, jdbc.queryForObject(
                "SELECT deleted FROM mall_favorite WHERE member_id = ? AND goods_id = ?",
                Integer.class, member, goods1), "恢复后 deleted=0");
    }

    /** 列表带商品名/主图/价格（经商品域内部接口批量补齐），按收藏时间倒序 */
    @Test
    void listEnrichedWithGoodsBrief() throws Exception {
        long member = createMember("收藏列表");
        String token = memberToken(member);
        toggle(token, goods1);
        toggle(token, goods2);

        JsonNode data = ok(get("/shop/favorite/list").header("Authorization", bearer(token))).get("data");
        assertEquals(2, data.get("total").asInt());
        JsonNode first = data.get("list").get(0);
        assertEquals(String.valueOf(goods2), first.get("goodsId").asText(), "后收藏的排最前");
        assertEquals("收藏商品二", first.get("goodsName").asText());
        assertEquals("/img/test.png", first.get("mainImage").asText());
        assertEquals(200L, first.get("priceMin").asLong());
        assertEquals(1, first.get("goodsStatus").asInt());

        // 取消后从列表消失
        toggle(token, goods2);
        assertEquals(1, ok(get("/shop/favorite/list").header("Authorization", bearer(token)))
                .get("data").get("total").asInt());
    }

    /** 会员之间不串数据：A 的收藏 B 看不到 */
    @Test
    void favoritesAreMemberScoped() throws Exception {
        String tokenA = memberToken(createMember("收藏隔离A"));
        String tokenB = memberToken(createMember("收藏隔离B"));
        toggle(tokenA, goods1);

        assertEquals(0, ok(get("/shop/favorite/list").header("Authorization", bearer(tokenB)))
                .get("data").get("total").asInt());
        assertFalse(check(tokenB, goods1));
    }

    /** 未登录访问收藏接口 → 401（不在匿名白名单） */
    @Test
    void anonymousRejected() throws Exception {
        mvc.perform(post("/shop/favorite/" + goods1)).andExpect(status().isUnauthorized());
        mvc.perform(get("/shop/favorite/check/" + goods1)).andExpect(status().isUnauthorized());
        mvc.perform(get("/shop/favorite/list")).andExpect(status().isUnauthorized());
    }

    // ===== 小工具 =====

    private boolean toggle(String token, long goodsId) throws Exception {
        return ok(post("/shop/favorite/" + goodsId).header("Authorization", bearer(token)))
                .get("data").get("favorited").asBoolean();
    }

    private boolean check(String token, long goodsId) throws Exception {
        return ok(get("/shop/favorite/check/" + goodsId).header("Authorization", bearer(token)))
                .get("data").get("favorited").asBoolean();
    }

    private GoodsBrief goods(long id, String name, long price) {
        GoodsBrief brief = new GoodsBrief();
        brief.setId(id);
        brief.setName(name);
        brief.setMainImage("/img/test.png");
        brief.setPriceMin(price);
        brief.setPriceMax(price);
        brief.setStatus(1);
        return brief;
    }
}
