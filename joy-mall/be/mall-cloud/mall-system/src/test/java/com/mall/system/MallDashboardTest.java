package com.mall.system;

import com.mall.service.MallDashboardService;
import com.mall.testsupport.MallIntegrationTestBase;
import com.mall.vo.MallDashboardVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6 T-01 经营看板口径回归（原单体 MallDashboardTest 改写）。
 *
 * <p>看板是「跨域只读聚合」，住在系统服务里。断言方式从「非负 + 结构存在」升级成
 * <b>增量断言</b>：先取基线，再插一批已知数据，断言 GMV/订单数/退款额/新会员的增量恰好等于造的数量。
 * 用增量而不是绝对值，是因为测试库里可能还有别的用例留下的数据，绝对值断言会假红。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MallDashboardTest extends MallIntegrationTestBase {

    @Autowired
    private MallDashboardService dashboardService;

    @AfterAll
    void tearDown() {
        tearDownFixtures();
    }

    @Test
    void overviewCountsOnlyWithinRange() {
        MallDashboardVO before = dashboardService.overview("30d");

        long member = createMember("看板会员");
        createMember("看板会员2");
        long goodsId = createGoods(createCategory(), "看板商品", 100L);
        long paidA = createOrder(member, 20, 100L, 1);
        createOrderItem(paidA, goodsId, 3, 100L);
        long paidB = createOrder(member, 20, 200L, 1);
        createOrderItem(paidB, goodsId, 1, 200L);
        createOrder(member, 10, 999L, 0);          // 未付款：不计 GMV，但也不算待发货
        createRefund(member, paidA, 4, 100L);       // 已退款：计入退款额
        createRefund(member, paidB, 1, 200L);       // 待审核：计入待审核 backlog

        MallDashboardVO after = dashboardService.overview("30d");

        assertEquals(before.getGmv() + 300L, after.getGmv(), "GMV 只统计已支付订单");
        assertEquals(before.getOrderCount() + 2L, after.getOrderCount(), "未支付订单不计单量");
        assertEquals(after.getGmv() / after.getOrderCount(), after.getAvgOrderAmount(),
                "客单价恒等于 floor(GMV/订单数)");
        assertEquals(before.getRefundAmount() + 100L, after.getRefundAmount(), "退款额只算 status=4");
        assertEquals(before.getPendingRefundAudit() + 1L, after.getPendingRefundAudit(), "待审核售后 backlog");
        assertEquals(before.getNewMembers() + 2L, after.getNewMembers(), "新增会员按注册时间统计");
        assertTrue(after.getRefundRate() >= 0 && after.getRefundRate() <= 1, "退款率必须落在 [0,1]");
    }

    /** Top 商品：按成交量降序、最多 5 条、字段齐全 */
    @Test
    void topGoodsAreSortedAndCapped() {
        MallDashboardVO vo = dashboardService.overview("7d");
        assertNotNull(vo.getTopGoods(), "topGoods 不得为 null");
        assertTrue(vo.getTopGoods().size() <= 5, "最多 5 条，实际 " + vo.getTopGoods().size());
        for (int i = 1; i < vo.getTopGoods().size(); i++) {
            long prev = vo.getTopGoods().get(i - 1).getQuantity();
            long cur = vo.getTopGoods().get(i).getQuantity();
            assertTrue(prev >= cur, "必须按成交量降序");
        }
        vo.getTopGoods().forEach(item -> {
            assertNotNull(item.getGoodsId());
            assertNotNull(item.getGoodsName());
            assertTrue(item.getQuantity() > 0, "成交量为正");
        });
    }

    /** 非法 range 静默回落 30d，不抛异常；三档区间都能出数 */
    @Test
    void invalidRangeFallsBackTo30d() {
        for (String range : new String[]{"today", "7d", "30d", "999d", null}) {
            MallDashboardVO vo = dashboardService.overview(range);
            assertNotNull(vo, "range=" + range + " 不得为空");
            assertTrue(vo.getGmv() >= 0, "range=" + range + " gmv>=0");
            assertTrue(vo.getTopGoods().size() <= 5, "range=" + range + " topGoods<=5");
        }
        assertEquals("30d", dashboardService.overview("999d").getRange(), "非法区间回落 30d");
    }

    // ===== 造数（看板只读，数据用 SQL 直接铺，避免依赖别的服务） =====

    private long createOrder(long memberId, int status, long payAmount, int payStatus) {
        String orderNo = TEST_MARK + "D-" + newPhone();
        jdbc.update("INSERT INTO mall_order (order_no, member_id, total_amount, freight_amount, discount_amount,"
                        + " pay_amount, status, pay_status, receiver_name, receiver_phone, receiver_address, pay_time)"
                        + " VALUES (?,?,?,0,0,?,?,?,?,?,?,?)",
                orderNo, memberId, payAmount, payAmount, status, payStatus,
                "看板收件人", "19900000000", "浙江省杭州市西湖区测试路 1 号",
                payStatus == 1 ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()) : null);
        return jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no = ?", Long.class, orderNo);
    }

    private void createOrderItem(long orderId, long goodsId, int qty, long amount) {
        jdbc.update("INSERT INTO mall_order_item (order_id, goods_id, sku_id, goods_name, price, quantity, total_amount)"
                + " VALUES (?,?,?,?,?,?,?)", orderId, goodsId, goodsId, "看板商品", amount / qty, qty, amount);
    }

    private void createRefund(long memberId, long orderId, int status, long amount) {
        String orderNo = jdbc.queryForObject("SELECT order_no FROM mall_order WHERE id = ?", String.class, orderId);
        jdbc.update("INSERT INTO mall_refund (refund_no, order_id, order_no, member_id, type, amount, reason, status)"
                        + " VALUES (?,?,?,?,1,?,?,?)",
                TEST_MARK + "R-" + newPhone(), orderId, orderNo, memberId, amount, "看板测试", status);
    }
}
