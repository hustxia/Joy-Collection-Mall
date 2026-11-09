package com.mall.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.testsupport.MallIntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6 T-01 商品域库存回归：把原单体 TradeFlowTest 里「不超卖 / 并发扣减 / 取消回补」的断言
 * 迁到库存的真正归属方。
 *
 * <p>为什么在商品域：库存只存在 {@code mall_sku}，订单域只是调用方。条件 UPDATE 是否真的
 * 不超卖，只能在商品域对着库断言；订单侧只验证「有没有按正确参数发起扣减/回补」。
 *
 * <p>同时覆盖 T-02 的模块上下文加载（本类本身就是商品服务的 {@code @SpringBootTest}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoodsStockInnerTest extends MallIntegrationTestBase {

    /** V10：C 端商品列表会补店铺名，商品域测试不真连商家服务 */
    @MockBean
    private MerchantInnerClient merchantInnerClient;

    private final ObjectMapper om = new ObjectMapper();

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    /** 扣减够了就扣，返回是否成功 */
    @Test
    void deductThenRestoreRoundTrip() throws Exception {
        long skuId = createGoodsWithSku("库存往返商品", 100L, 10);

        assertTrue(deduct(skuId, 3), "库存充足应扣减成功");
        assertEquals(7, stockOf(skuId), "扣 3 后剩 7");

        restore(skuId, 3);
        assertEquals(10, stockOf(skuId), "回补 3 后回到 10");
    }

    /** 库存不足：返回 false 且库存分毫不动（不出现负库存） */
    @Test
    void deductRejectedWhenInsufficient() throws Exception {
        long skuId = createGoodsWithSku("库存不足商品", 100L, 2);

        assertFalse(deduct(skuId, 5), "库存 2 扣 5 必须失败");
        assertEquals(2, stockOf(skuId), "失败不得扣成负数");
    }

    /** 多 SKU 整体原子：第二个不足时，第一个已扣的要回补 */
    @Test
    void deductIsAtomicAcrossSkus() throws Exception {
        long enough = createGoodsWithSku("原子-A", 100L, 5);
        long scarce = createGoodsWithSku("原子-B", 100L, 1);

        assertFalse(deduct(Map.of(enough, 2, scarce, 2)), "任一 SKU 不足即整体失败");
        assertEquals(5, stockOf(enough), "已扣的 A 必须回补");
        assertEquals(1, stockOf(scarce), "B 不动");
    }

    /** 并发 8 抢 5：恰 5 单成功、库存精确归零（原 TradeFlowTest.concurrentNoOversell 同口径） */
    @Test
    void concurrentDeductNeverOversells() throws Exception {
        long skuId = createGoodsWithSku("并发抢购商品", 100L, 5);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (deduct(skuId, 1)) {
                        success.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 抛错即视为失败，落到断言里
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未就绪");
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发扣减超时");
        pool.shutdownNow();

        assertEquals(5, success.get(), "8 线程抢 5 库存，恰好 5 次成功，实际 " + success.get());
        assertEquals(0, stockOf(skuId), "库存精确归零不为负");
    }

    /** 销量可加可减，且被 GREATEST 兜住不出现负数（退货退款回减口径） */
    @Test
    void salesAccumulateAndClampAtZero() throws Exception {
        long skuId = createGoodsWithSku("销量商品", 100L, 10);

        addSales(skuId, 5);
        assertEquals(5, salesOf(skuId));

        addSales(skuId, -9);
        assertEquals(0, salesOf(skuId), "回减过头按 0 兜底，不出现负销量");
    }

    /** 内部端点返回裸对象（不包 Result）：Feign 反序列化契约的服务端一半 */
    @Test
    void innerEndpointReturnsRawPayload() throws Exception {
        long skuId = createGoodsWithSku("裸对象校验商品", 199L, 3);

        MvcResult result = mvc.perform(get("/inner/sku/by-ids").param("ids", String.valueOf(skuId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode skuArray = om.readTree(result.getResponse().getContentAsString());
        assertTrue(skuArray.isArray(), "/inner 必须返回数组本体，不能是 {code,data} 包装");
        assertEquals(skuId, skuArray.get(0).get("id").asLong());
        assertEquals(199L, skuArray.get(0).get("price").asLong());
        assertEquals(3, skuArray.get(0).get("stock").asInt());
    }

    // ===== /inner 调用小工具（内部端点返回裸值，不走 Result 壳）=====

    private boolean deduct(long skuId, int qty) throws Exception {
        MvcResult result = mvc.perform(post("/inner/sku/deduct").contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + skuId + "\":" + qty + "}")).andExpect(status().isOk()).andReturn();
        return Boolean.parseBoolean(result.getResponse().getContentAsString().trim());
    }

    private boolean deduct(java.util.Map<Long, Integer> deltas) throws Exception {
        MvcResult result = mvc.perform(post("/inner/sku/deduct").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(deltas))).andExpect(status().isOk()).andReturn();
        return Boolean.parseBoolean(result.getResponse().getContentAsString().trim());
    }

    private void restore(long skuId, int qty) throws Exception {
        mvc.perform(post("/inner/sku/restore").contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + skuId + "\":" + qty + "}")).andExpect(status().isOk());
    }

    private void addSales(long skuId, int delta) throws Exception {
        mvc.perform(post("/inner/sku/sales").contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + skuId + "\":" + delta + "}")).andExpect(status().isOk());
    }
}
