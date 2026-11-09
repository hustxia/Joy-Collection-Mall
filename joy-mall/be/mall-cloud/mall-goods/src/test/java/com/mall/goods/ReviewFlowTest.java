package com.mall.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.MerchantInnerClient;
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * V7 商品评价回归：提交（订单域 can-review 校验 + 一项一评）、C 端列表聚合、
 * 后台回复/隐藏、我的评价、商品详情评分聚合。
 *
 * <p>「订单项是否可评」由订单域 /inner/order-item/can-review 判定，这里用 mock 的
 * {@link OrderInnerClient} 模拟（与 RefundFlowTest 同套路：跨域快照一律 mock Feign，
 * 本域数据直插测试库断言）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewFlowTest extends MallIntegrationTestBase {

    /** V10：C 端商品列表会补店铺名 */
    @MockBean
    private MerchantInnerClient merchantInnerClient;

    @MockBean
    private OrderInnerClient orderInnerClient;

    private long memberA;
    private long memberB;
    private String tokenA;
    private String tokenB;
    private String adminToken;
    private long goodsId;
    private long skuId;

    /** 本用例里「订单域认定为可评」的订单项集合 */
    private final Set<Long> reviewableItems = new HashSet<>();
    /** 本用例产生的评价行（tearDown 自清） */
    private final Set<Long> reviewGoodsIds = new HashSet<>();

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("评价A");
        memberB = createMember("评价B");
        tokenA = memberToken(memberA);
        tokenB = memberToken(memberB);
        adminToken = adminToken();
        skuId = createGoodsWithSku("评价商品", 100L, 10);
        goodsId = jdbc.queryForObject("SELECT goods_id FROM mall_sku WHERE id = ?", Long.class, skuId);
        reviewGoodsIds.add(goodsId);
    }

    @BeforeEach
    void stubCanReview() {
        reviewableItems.clear();
        when(orderInnerClient.canReview(anyLong(), anyLong()))
                .thenAnswer(inv -> reviewableItems.contains(inv.<Long>getArgument(0)));
    }

    @AfterAll
    void tearDown() {
        for (Long gid : reviewGoodsIds) {
            jdbc.update("DELETE FROM mall_review WHERE goods_id = ?", gid);
        }
        jdbc.update("DELETE FROM mall_review WHERE member_id IN (?, ?)", memberA, memberB);
        tearDownFixtures();
    }

    /** 可评 → 提交成功落库；同订单项二次提交被唯一键兜成 400「已评价过」 */
    @Test
    void submitSuccessAndDuplicateRejected() throws Exception {
        long orderItemId = newItemId();
        reviewableItems.add(orderItemId);

        ok(mvc.perform(postJsonRaw("/shop/review", tokenA,
                "{\"orderItemId\":" + orderItemId + ",\"orderId\":6001,"
                        + "\"goodsId\":" + goodsId + ",\"skuId\":" + skuId
                        + ",\"rating\":5,\"content\":\"很好用\",\"memberNickname\":\"张三\"}")));

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_review WHERE order_item_id = ? AND member_id = ? AND deleted = 0",
                Integer.class, orderItemId, memberA);
        assertEquals(1, cnt, "提交后应落库一条");

        JsonNode err = bizError(mvc.perform(postJsonRaw("/shop/review", tokenA,
                "{\"orderItemId\":" + orderItemId + ",\"orderId\":6001,"
                        + "\"goodsId\":" + goodsId + ",\"skuId\":" + skuId + ",\"rating\":4}")), 400);
        assertEquals("已评价过", err.get("msg").asText(), "重复评价必须被唯一键拦截");
    }

    /** 订单域判定不可评 → 400「该商品不可评价」，且不落库 */
    @Test
    void submitRejectedWhenNotReviewable() throws Exception {
        long orderItemId = newItemId();

        JsonNode err = bizError(mvc.perform(postJsonRaw("/shop/review", tokenA,
                "{\"orderItemId\":" + orderItemId + ",\"orderId\":6002,"
                        + "\"goodsId\":" + goodsId + ",\"skuId\":" + skuId + ",\"rating\":5}")), 400);
        assertEquals("该商品不可评价", err.get("msg").asText());

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_review WHERE order_item_id = ? AND deleted = 0",
                Integer.class, orderItemId);
        assertEquals(0, cnt, "不可评不得落库");
    }

    /** 评分越界 400；缺订单项字段 400 */
    @Test
    void submitValidatesParams() throws Exception {
        long orderItemId = newItemId();
        reviewableItems.add(orderItemId);
        bizError(mvc.perform(postJsonRaw("/shop/review", tokenA,
                "{\"orderItemId\":" + orderItemId + ",\"orderId\":6003,"
                        + "\"goodsId\":" + goodsId + ",\"skuId\":" + skuId + ",\"rating\":6}")), 400);
        bizError(mvc.perform(postJsonRaw("/shop/review", tokenA, "{\"rating\":5}")), 400);
    }

    /** C 端列表：status=0 分页 + avg/total 聚合；隐藏后列表与聚合都剔除 */
    @Test
    void goodsListAggregatesAndHideFilters() throws Exception {
        long g2 = createGoods(createCategory(), "评价聚合商品", 100L);
        reviewGoodsIds.add(g2);
        long s2 = createSku(g2, 100L, 5);
        long r1 = insertReview(g2, s2, memberA, 5, "好评");
        insertReview(g2, s2, memberB, 3, "一般");

        JsonNode list = ok(mvc.perform(get("/shop/review/goods/" + g2)
                .header("Authorization", bearer(tokenA))));
        JsonNode data = list.get("data");
        assertEquals(2, data.get("total").asInt());
        assertEquals(4.0, data.get("avg").asDouble(), 0.0001, "5 与 3 平均 4.0");
        assertEquals(2, data.get("records").size());

        // 后台隐藏 r1：列表只剩 1 条，avg 变为 3.0
        ok(mvc.perform(post("/mall_review/hide").header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"id\":" + r1 + ",\"status\":1}")));
        JsonNode after = ok(mvc.perform(get("/shop/review/goods/" + g2)
                .header("Authorization", bearer(tokenA))));
        assertEquals(1, after.get("data").get("total").asInt(), "隐藏后列表不可见");
        assertEquals(3.0, after.get("data").get("avg").asDouble(), 0.0001, "聚合同样剔除隐藏行");

        // 恢复 + 回复：reply/reply_time 落库
        ok(mvc.perform(post("/mall_review/hide").header("Authorization", bearer(adminToken))
                .contentType("application/json").content("{\"id\":" + r1 + ",\"status\":0}")));
        ok(mvc.perform(post("/mall_review/reply").header("Authorization", bearer(adminToken))
                .contentType("application/json").content("{\"id\":" + r1 + ",\"reply\":\"感谢支持\"}")));
        String reply = jdbc.queryForObject("SELECT reply FROM mall_review WHERE id = ?", String.class, r1);
        assertEquals("感谢支持", reply);
        Integer replied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mall_review WHERE id = ? AND reply_time IS NOT NULL", Integer.class, r1);
        assertEquals(1, replied, "回复必须写 reply_time");

        // 后台分页筛选：goodsId + rating
        JsonNode page = ok(mvc.perform(post("/mall_review/page").header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"pageNum\":1,\"pageSize\":10,\"goodsId\":" + g2 + ",\"rating\":3}")));
        assertEquals(1, page.get("data").get("total").asInt(), "rating=3 只剩会员B那条");
    }

    /** 我的评价只出本人；商品详情聚合 ratingAvg/ratingCount（匿名可读） */
    @Test
    void myListAndDetailAggregate() throws Exception {
        long g3 = createGoods(createCategory(), "评价我的商品", 100L);
        reviewGoodsIds.add(g3);
        long s3 = createSku(g3, 100L, 5);
        // 独立会员 C：memberA/B 在前面用例已有评价，/my 口径必须用干净会员断言
        long memberC = createMember("评价C");
        String tokenC = memberToken(memberC);
        insertReview(g3, s3, memberC, 5, "C 的评价");
        insertReview(g3, s3, memberB, 4, "B 的评价");

        JsonNode my = ok(mvc.perform(get("/shop/review/my").header("Authorization", bearer(tokenC))));
        JsonNode records = my.get("data").get("records");
        assertEquals(1, records.size(), "我的评价只出本人");
        assertEquals(String.valueOf(memberC), records.get(0).get("memberId").asText());

        // 详情接口在匿名白名单内：不带 token 也要出聚合
        JsonNode detail = ok(mvc.perform(get("/shop/goods/" + g3)));
        assertEquals(4.5, detail.get("data").get("ratingAvg").asDouble(), 0.0001);
        assertEquals(2, detail.get("data").get("ratingCount").asInt());

        // 无评价商品：avg=0/count=0
        long g4 = createGoods(createCategory(), "无评价商品", 100L);
        reviewGoodsIds.add(g4);
        JsonNode empty = ok(mvc.perform(get("/shop/goods/" + g4)));
        assertEquals(0.0, empty.get("data").get("ratingAvg").asDouble(), 0.0001);
        assertEquals(0, empty.get("data").get("ratingCount").asInt());
    }

    // ===== 小工具 =====

    /** 生成不撞唯一键的订单项 ID（测试库订单项为雪花 ID，取 8 开头大数段避开） */
    private long newItemId() {
        return 8_000_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000);
    }

    /** 直插一条评价（绕过 can-review 的列表/聚合场景造数），返回评价 ID */
    private long insertReview(long goodsId, long skuId, long memberId, int rating, String content) {
        long orderItemId = newItemId();
        jdbc.update("INSERT INTO mall_review (order_id, order_item_id, goods_id, sku_id, member_id,"
                        + " member_nickname, rating, content, images, status) VALUES (?,?,?,?,?,?,?,?,'',0)",
                6000L, orderItemId, goodsId, skuId, memberId, "IT昵称", rating, content);
        Long id = jdbc.queryForObject(
                "SELECT id FROM mall_review WHERE order_item_id = ?", Long.class, orderItemId);
        assertTrue(id != null);
        return id;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJsonRaw(
            String url, String token, String body) {
        return post(url).header("Authorization", bearer(token))
                .contentType("application/json").content(body);
    }
}
