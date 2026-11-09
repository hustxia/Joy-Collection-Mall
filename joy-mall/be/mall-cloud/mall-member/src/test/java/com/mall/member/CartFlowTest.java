package com.mall.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6 T-01 购物车回归（含 V5 那个撞唯一键的线上问题）。
 *
 * <p>核心是删除口径：{@code uk_member_sku(member_id, sku_id, deleted)} 含 deleted 列，
 * 软删会让「删同一 SKU 第二次」撞唯一键 500，所以删除走物理删。
 * 「加购→删除→再加购→再删除」把这条件锁死，改回软删立刻红。
 *
 * <p>每个用例自带会员（购物车行会累积，共用会员会让断言互相污染）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CartFlowTest extends MallIntegrationTestBase {

    @MockBean
    private GoodsInnerClient goodsInnerClient;

    private long goodsId;
    private long skuId;

    @BeforeAll
    void prepareFixtures() {
        goodsId = createGoods(createCategory(), "购物车商品", 100L);
        skuId = createSku(goodsId, 100L, 5);
    }

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    @BeforeEach
    void stubGoods() {
        when(goodsInnerClient.skuByIds(anyList())).thenReturn(List.of(sku(skuId, 100L, 5)));
        when(goodsInnerClient.goodsByIds(anyList())).thenReturn(List.of(goods(goodsId, "购物车商品", 100L, 1)));
    }

    /** 加购 → 列表带商品快照；同 SKU 再买累加数量 */
    @Test
    void addAccumulatesAndListShowsSnapshot() throws Exception {
        String token = newMemberToken("购物车累加");

        add(token, skuId, 2);
        add(token, skuId, 3);

        JsonNode rows = cartRows(token);
        assertEquals(1, rows.size(), "同 SKU 只保留一行");
        assertEquals(5, rows.get(0).get("num").asInt(), "数量累加 2+3");
        assertEquals("购物车商品", rows.get(0).get("goodsName").asText());
        assertEquals(100L, rows.get(0).get("price").asLong());
        assertEquals(5, rows.get(0).get("stock").asInt());
    }

    /** 累加后超库存 → 400，且数量不变 */
    @Test
    void addRejectedWhenExceedingStock() throws Exception {
        String token = newMemberToken("购物车超库存");

        add(token, skuId, 3);
        bizError(post("/shop/cart/add").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":" + skuId + ",\"num\":4}"), 400);

        assertEquals(3, cartRows(token).get(0).get("num").asInt(), "失败不改变已有数量");
    }

    /** V5 回归：加购→删除→再加购→再删除，全程不得 500（物理删除而非软删） */
    @Test
    void deleteTwiceAfterReAddDoesNotHitUniqueKey() throws Exception {
        long member = createMember("购物车删除口径");
        String token = memberToken(member);

        add(token, skuId, 1);
        ok(delete("/shop/cart/" + firstCartId(token)).header("Authorization", bearer(token)));

        // 再买同一个 SKU：软删残留会让 uk_member_sku 撞键
        add(token, skuId, 1);
        ok(delete("/shop/cart/" + firstCartId(token)).header("Authorization", bearer(token)));

        assertEquals(0, cartRows(token).size(), "两次删除后购物车为空");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_cart WHERE member_id = ?", Integer.class, member), "物理删除不留行");
    }

    /** 改数量与勾选；数量超库存 400 */
    @Test
    void updateNumAndChecked() throws Exception {
        String token = newMemberToken("购物车改数量");
        add(token, skuId, 1);
        long cartId = firstCartId(token);

        ok(put("/shop/cart/update").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + cartId + ",\"num\":4,\"checked\":0}"));
        JsonNode row = cartRows(token).get(0);
        assertEquals(4, row.get("num").asInt());
        assertEquals(0, row.get("checked").asInt());

        bizError(put("/shop/cart/update").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + cartId + ",\"num\":9}"), 400);
    }

    /** 越权：删/改他人购物车行被拒；本人的行不受影响 */
    @Test
    void cannotTouchOthersCart() throws Exception {
        String owner = newMemberToken("购物车归属A");
        String other = newMemberToken("购物车归属B");
        add(owner, skuId, 1);
        long cartId = firstCartId(owner);

        bizError(delete("/shop/cart/" + cartId).header("Authorization", bearer(other)), 403);
        bizError(put("/shop/cart/update").header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + cartId + ",\"num\":2}"), 403);

        JsonNode rows = cartRows(owner);
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).get("num").asInt(), "他人的改删不得生效");
    }

    /** 未登录加购 → 401；未登录查列表 → 401 */
    @Test
    void anonymousRejected() throws Exception {
        mvc.perform(post("/shop/cart/add").contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":" + skuId + ",\"num\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/shop/cart/list")).andExpect(status().isUnauthorized());
    }

    // ===== 小工具 =====

    private String newMemberToken(String nickname) {
        return memberToken(createMember(nickname));
    }

    private void add(String token, long sku, int num) throws Exception {
        ok(post("/shop/cart/add").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":" + sku + ",\"num\":" + num + "}"));
    }

    private JsonNode cartRows(String token) throws Exception {
        return ok(get("/shop/cart/list").header("Authorization", bearer(token))).get("data");
    }

    private long firstCartId(String token) throws Exception {
        return Long.parseLong(cartRows(token).get(0).get("id").asText());
    }

    private SkuBrief sku(long id, long price, int stock) {
        SkuBrief brief = new SkuBrief();
        brief.setId(id);
        brief.setGoodsId(goodsId);
        brief.setSkuCode("IT-CART-" + id);
        brief.setSpecJson("[]");
        brief.setPrice(price);
        brief.setStock(stock);
        brief.setStatus(1);
        return brief;
    }

    private GoodsBrief goods(long id, String name, long price, int status) {
        GoodsBrief brief = new GoodsBrief();
        brief.setId(id);
        brief.setName(name);
        brief.setMainImage("/img/test.png");
        brief.setPriceMin(price);
        brief.setPriceMax(price);
        brief.setStatus(status);
        return brief;
    }
}
