package com.mall.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V15 评价增强（REQ-P1-05）：追评 30 天窗口、追评仅一次、有用数幂等（同一会员重复点只计一次）、
 * 按标签筛选（C 端列表 + 后台分页）。
 *
 * <p>套路与 {@link ReviewFlowTest} 一致：跨域 can-review 一律 mock，本域数据直插测试库断言。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewV15FlowTest extends MallIntegrationTestBase {

    @MockBean
    private MerchantInnerClient merchantInnerClient;

    @MockBean
    private OrderInnerClient orderInnerClient;

    private long memberA;
    private long memberB;
    private String tokenA;
    private String tokenB;
    private String adminToken;

    private final Set<Long> reviewGoodsIds = new HashSet<>();
    private final Set<Long> reviewIds = new HashSet<>();

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("V15评价A");
        memberB = createMember("V15评价B");
        tokenA = memberToken(memberA);
        tokenB = memberToken(memberB);
        adminToken = adminToken();
    }

    @AfterAll
    void tearDown() {
        for (Long rid : reviewIds) {
            jdbc.update("DELETE FROM mall_review_useful WHERE review_id = ?", rid);
        }
        for (Long gid : reviewGoodsIds) {
            jdbc.update("DELETE FROM mall_review WHERE goods_id = ?", gid);
        }
        tearDownFixtures();
    }

    /** 追评：30 天内成功一次；二次追评 400；他人评价 403；超 30 天 400；详情两条口径（content + appendContent） */
    @Test
    void appendWindowAndOnce() throws Exception {
        long reviewId = insertReview(memberA, 5, "质量很好", "物流快", now());
        reviewIds.add(reviewId);

        // 他人不能追评别人的评价
        bizError(postJsonRaw("/shop/review/append", tokenB,
                "{\"reviewId\":" + reviewId + ",\"content\":\"蹭一个\"}"), 403);

        // 30 天内追评成功：append_content/append_time/append_images 落库
        ok(postJsonRaw("/shop/review/append", tokenA,
                "{\"reviewId\":" + reviewId + ",\"content\":\"用了半个月来追评\",\"images\":\"/img/a1.png\"}"));
        var row = jdbc.queryForMap(
                "SELECT append_content, append_time, append_images FROM mall_review WHERE id = ?", reviewId);
        assertEquals("用了半个月来追评", row.get("append_content"), "追评内容应落库");
        assertTrue(row.get("append_time") != null, "追评时间应落库");
        assertEquals("/img/a1.png", row.get("append_images"), "追评图片应落库");

        // 每条评价仅可追评一次
        JsonNode err = bizError(postJsonRaw("/shop/review/append", tokenA,
                "{\"reviewId\":" + reviewId + ",\"content\":\"再追一次\"}"), 400);
        assertEquals("已追评过（每条评价仅可追评一次）", err.get("msg").asText());

        // 详情：原评 + 追评都在（验收「详情展示两条并标注追评」的数据口径）
        JsonNode detail = ok(get("/shop/review/" + reviewId).header("Authorization", bearer(tokenA)));
        assertEquals("质量很好", detail.get("data").get("review").get("content").asText());
        assertEquals("用了半个月来追评", detail.get("data").get("review").get("appendContent").asText());
        assertTrue(!detail.get("data").get("review").get("appendTime").isNull(), "追评时间应下发");
        assertEquals(0, detail.get("data").get("usefulCount").asInt());

        // 超 30 天窗口：把 create_time 拨回 31 天前
        long review2 = insertReview(memberA, 4, "还行", "", now());
        reviewIds.add(review2);
        jdbc.update("UPDATE mall_review SET create_time = DATE_SUB(NOW(), INTERVAL 31 DAY) WHERE id = ?", review2);
        JsonNode late = bizError(postJsonRaw("/shop/review/append", tokenA,
                "{\"reviewId\":" + review2 + ",\"content\":\"晚了\"}"), 400);
        assertEquals("已过追评期（原评价后 30 天内可追评）", late.get("msg").asText());
        assertNull(jdbc.queryForObject(
                "SELECT append_content FROM mall_review WHERE id = ?", String.class, review2),
                "过窗追评不得落库");
    }

    /** 有用数幂等：同一会员重复点只计一次；取消幂等；再点复活仍只一行 */
    @Test
    void usefulIdempotent() throws Exception {
        long reviewId = insertReview(memberA, 5, "值得买", "包装好", now());
        reviewIds.add(reviewId);

        // A 点有用 → 1
        JsonNode r1 = ok(postJsonRaw("/shop/review/useful", tokenA,
                "{\"reviewId\":" + reviewId + ",\"liked\":true}"));
        assertEquals(1, r1.get("data").get("usefulCount").asInt());
        assertTrue(r1.get("data").get("liked").asBoolean());

        // A 重复点 → 仍 1，明细表仍一行（幂等验收口径）
        JsonNode r2 = ok(postJsonRaw("/shop/review/useful", tokenA,
                "{\"reviewId\":" + reviewId + ",\"liked\":true}"));
        assertEquals(1, r2.get("data").get("usefulCount").asInt(), "同一会员重复点只计一次");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_review_useful WHERE review_id = ?", Integer.class, reviewId),
                "唯一键下明细至多一行");

        // B 点 → 2；B 取消 → 1；B 重复取消 → 仍 1 不报错；B 再点 → 复活仍一行
        ok(postJsonRaw("/shop/review/useful", tokenB, "{\"reviewId\":" + reviewId + ",\"liked\":true}"));
        assertEquals(2, usefulCountOf(reviewId));
        ok(postJsonRaw("/shop/review/useful", tokenB, "{\"reviewId\":" + reviewId + ",\"liked\":false}"));
        assertEquals(1, usefulCountOf(reviewId));
        ok(postJsonRaw("/shop/review/useful", tokenB, "{\"reviewId\":" + reviewId + ",\"liked\":false}"));
        assertEquals(1, usefulCountOf(reviewId), "重复取消幂等");
        JsonNode r3 = ok(postJsonRaw("/shop/review/useful", tokenB, "{\"reviewId\":" + reviewId + ",\"liked\":true}"));
        assertEquals(2, r3.get("data").get("usefulCount").asInt(), "取消后再点应复活");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_review_useful WHERE review_id = ?", Integer.class, reviewId),
                "反复点/取消不产生多行");
        // 冗余计数列与明细对账
        assertEquals(2, jdbc.queryForObject(
                "SELECT useful_count FROM mall_review WHERE id = ?", Integer.class, reviewId));

        // 不存在的评价 → 400
        bizError(postJsonRaw("/shop/review/useful", tokenA, "{\"reviewId\":1,\"liked\":true}"), 400);
    }

    /** 标签筛选：C 端列表 tag 参数 + 后台分页 tag 参数 */
    @Test
    void tagFilter() throws Exception {
        long categoryId = createCategory();
        long goodsId = createGoods(categoryId, "V15标签商品", 100L);
        reviewGoodsIds.add(goodsId);
        long skuId = createSku(goodsId, 100L, 5);
        long rLogistics = insertReviewOnGoods(goodsId, skuId, memberA, 5, "发货快", "物流快,包装好", now());
        long rQuality = insertReviewOnGoods(goodsId, skuId, memberB, 4, "手感不错", "质量好", now());
        reviewIds.add(rLogistics);
        reviewIds.add(rQuality);

        // C 端：tag=物流快 只出 A 那条；无 tag 全出
        JsonNode filtered = ok(get("/shop/review/goods/" + goodsId + "?tag=物流快")
                .header("Authorization", bearer(tokenA)));
        assertEquals(1, filtered.get("data").get("total").asInt());
        assertEquals("发货快", filtered.get("data").get("records").get(0).get("content").asText());
        JsonNode all = ok(get("/shop/review/goods/" + goodsId).header("Authorization", bearer(tokenA)));
        assertEquals(2, all.get("data").get("total").asInt());

        // 后台：tag=质量好 只出 B 那条
        JsonNode page = ok(post("/mall_review/page").header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"pageNum\":1,\"pageSize\":10,\"goodsId\":" + goodsId + ",\"tag\":\"质量好\"}"));
        assertEquals(1, page.get("data").get("total").asInt());
        assertEquals("手感不错", page.get("data").get("records").get(0).get("content").asText());
        assertEquals("质量好", page.get("data").get("records").get(0).get("tags").asText());
    }

    // ===== 小工具 =====

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJsonRaw(
            String url, String token, String body) {
        return post(url).header("Authorization", bearer(token))
                .contentType("application/json").content(body);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private int usefulCountOf(long reviewId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_review_useful WHERE review_id = ? AND deleted = 0",
                Integer.class, reviewId);
    }

    /** 直插一条评价（可指定商品与标签），返回评价 ID */
    private long insertReviewOnGoods(long goodsId, long skuId, long memberId, int rating,
                                     String content, String tags, long seed) {
        long orderItemId = 8_500_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        jdbc.update("INSERT INTO mall_review (order_id, order_item_id, goods_id, sku_id, member_id,"
                        + " member_nickname, rating, content, images, tags, status) VALUES (?,?,?,?,?,?,?,?,'',?,0)",
                7000L, orderItemId, goodsId, skuId, memberId, "V15昵称", rating, content, tags);
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_review WHERE order_item_id = ?", Long.class, orderItemId);
        assertTrue(id != null);
        return id;
    }

    /** 直插一条评价（自建商品），返回评价 ID */
    private long insertReview(long memberId, int rating, String content, String tags, long seed) {
        long categoryId = createCategory();
        long goodsId = createGoods(categoryId, "V15商品-" + seed, 100L);
        reviewGoodsIds.add(goodsId);
        long skuId = createSku(goodsId, 100L, 5);
        return insertReviewOnGoods(goodsId, skuId, memberId, rating, content, tags, seed);
    }
}
