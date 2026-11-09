package com.mall.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.testsupport.MallIntegrationTestBase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V6 T-02/T-01 营销域券核销回归（顺带覆盖本模块上下文加载）。
 *
 * <p>券的「锁—核销—释放」是全链路里最容易出并发问题的一段：
 * 锁券必须靠条件 UPDATE（status=1 且 order_id IS NULL）保证同一张券只能被一个订单占住；
 * 释放只能放「还没核销」的券，已核销（status=2）不许被释放回退，否则同一张券能抵两次。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponRecordInnerTest extends MallIntegrationTestBase {

    private final ObjectMapper om = new ObjectMapper();

    private long memberA;
    private long memberB;
    private long couponId;

    @MockBean
    private MemberInnerClient memberInnerClient;
    @MockBean
    private GoodsInnerClient goodsInnerClient;
    @MockBean
    private OrderInnerClient orderInnerClient;

    @AfterAll
    void tearDown() {
        tearDownFixtures();
        jdbc.update("DELETE FROM mall_coupon_record WHERE coupon_id = ?", couponId);
        jdbc.update("DELETE FROM mall_coupon WHERE id = ?", couponId);
    }

    @BeforeAll
    void prepareFixtures() {
        memberA = createMember("营销A");
        memberB = createMember("营销B");
        jdbc.update("INSERT INTO mall_coupon (name, type, amount, threshold_amount, total_count, received_count,"
                        + " per_limit, start_time, end_time, status) VALUES (?,1,30,100,100,0,1,"
                        + " NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 1)",
                TEST_MARK + "满减券");
        couponId = jdbc.queryForObject("SELECT id FROM mall_coupon WHERE name = ?", Long.class,
                TEST_MARK + "满减券");
    }

    /** 同一张券只能被一个订单锁定；锁第二单返回 false 且原归属不变 */
    @Test
    void lockIsExclusive() throws Exception {
        long recordId = createRecord(memberA);

        assertTrue(lock(recordId, 9001L), "首次锁券应成功");
        assertEquals(9001L, orderIdOf(recordId), "锁券只占位 order_id");
        assertEquals(1, statusOfRecord(recordId), "锁券不改 status（支付才置 2）");

        assertFalse(lock(recordId, 9002L), "已被别的订单占住，第二次必须失败");
        assertEquals(9001L, orderIdOf(recordId), "失败不得改归属");
    }

    /** 支付核销：status→2 且写 used_time；核销后不可再次锁定、不可被释放 */
    @Test
    void useByOrderMarksUsedAndIsNotReleasable() throws Exception {
        long recordId = createRecord(memberA);
        assertTrue(lock(recordId, 9101L));

        postEmpty("/inner/coupon-record/use-by-order?orderId=9101");
        assertEquals(2, statusOfRecord(recordId), "核销后 status=2");
        assertNotNull(jdbc.queryForObject("SELECT used_time FROM mall_coupon_record WHERE id = ?",
                java.sql.Timestamp.class, recordId), "核销必须写 used_time");

        assertFalse(lock(recordId, 9102L), "已核销的券不能再锁");

        postEmpty("/inner/coupon-record/release-by-order?orderId=9101");
        assertEquals(2, statusOfRecord(recordId), "释放只对 status=1 生效，已核销不许回退");
    }

    /** 取消/关单释放：order_id 置空、status 保持 1，可被下一单重新锁定 */
    @Test
    void releaseUnlocksForNextOrder() throws Exception {
        long recordId = createRecord(memberA);
        assertTrue(lock(recordId, 9201L));

        postEmpty("/inner/coupon-record/release-by-order?orderId=9201");
        assertNull(orderIdOf(recordId), "释放后 order_id 置空");
        assertEquals(1, statusOfRecord(recordId), "status 保持可用");

        assertTrue(lock(recordId, 9202L), "释放后可被新订单锁定");
    }

    /** 可用券查询：只返回本人的、未核销、未被占用的记录 */
    @Test
    void availableRecordsFilteredAndScoped() throws Exception {
        long mine = createRecord(memberA);
        createRecord(memberB);
        long locked = createRecord(memberA);
        assertTrue(lock(locked, 9301L));
        long used = createRecord(memberA);
        assertTrue(lock(used, 9302L));
        postEmpty("/inner/coupon-record/use-by-order?orderId=9302");

        MvcResult result = mvc.perform(get("/inner/coupon-record").param("memberId", String.valueOf(memberA)))
                .andExpect(status().isOk()).andReturn();
        JsonNode records = om.readTree(result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(records.isArray(), "/inner 必须返回裸数组");
        boolean containsMine = false;
        for (JsonNode record : records) {
            long id = record.get("id").asLong();
            assertTrue(id != locked && id != used, "已占用/已核销的券不应出现在可用列表");
            assertEquals(memberA, record.get("memberId").asLong(), "只返回本人券");
            if (id == mine) {
                containsMine = true;
            }
        }
        assertTrue(containsMine, "可用的券必须出现在列表里");
    }

    /** 券详情批量查询（订单确认页取面额）：裸数组 + 面额字段正确 */
    @Test
    void couponByIdsReturnsBareArray() throws Exception {
        MvcResult result = mvc.perform(get("/inner/coupon/by-ids").param("ids", String.valueOf(couponId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode coupons = om.readTree(result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, coupons.size());
        assertEquals(30L, coupons.get(0).get("amount").asLong());
        assertEquals(100L, coupons.get(0).get("thresholdAmount").asLong());
    }

    // ===== 小工具 =====

    private long createRecord(long memberId) {
        jdbc.update("INSERT INTO mall_coupon_record (coupon_id, member_id, status) VALUES (?,?,1)", couponId, memberId);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM mall_coupon_record WHERE coupon_id = ? AND member_id = ?",
                Long.class, couponId, memberId);
    }

    private boolean lock(long recordId, long orderId) throws Exception {
        MvcResult result = mvc.perform(post("/inner/coupon-record/lock")
                        .param("recordId", String.valueOf(recordId))
                        .param("orderId", String.valueOf(orderId)))
                .andExpect(status().isOk()).andReturn();
        return Boolean.parseBoolean(result.getResponse().getContentAsString().trim());
    }

    private void postEmpty(String url) throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
    }

    private Long orderIdOf(long recordId) {
        return jdbc.queryForObject("SELECT order_id FROM mall_coupon_record WHERE id = ?", Long.class, recordId);
    }

    private int statusOfRecord(long recordId) {
        Integer status = jdbc.queryForObject("SELECT status FROM mall_coupon_record WHERE id = ?",
                Integer.class, recordId);
        return status == null ? -1 : status;
    }
}
