package com.mall.service.impl;

import com.mall.service.MallDashboardService;
import com.mall.vo.MallDashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 经营看板 Service 实现（V4 P0-2）
 * 聚合查询直接走 JdbcTemplate，避免为统计单独建 Mapper；所有金额以「分」返回。
 */
@Service
public class MallDashboardServiceImpl implements MallDashboardService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 已支付订单：pay_status=1 且未被逻辑删除 */
    private static final String PAID_WHERE = "pay_status = 1 AND deleted = 0";

    @Override
    public MallDashboardVO overview(String range) {
        LocalDateTime start = rangeStart(normalize(range));

        MallDashboardVO vo = new MallDashboardVO();
        vo.setRange(normalize(range));

        // GMV / 订单数（统计区间内已支付订单）
        Long gmv = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(pay_amount), 0) FROM mall_order WHERE " + PAID_WHERE + " AND create_time >= ?",
                Long.class, start);
        Long orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_order WHERE " + PAID_WHERE + " AND create_time >= ?",
                Long.class, start);
        gmv = gmv == null ? 0L : gmv;
        orderCount = orderCount == null ? 0L : orderCount;
        vo.setGmv(gmv);
        vo.setOrderCount(orderCount);
        vo.setAvgOrderAmount(orderCount > 0 ? gmv / orderCount : 0L);

        // 退款金额 / 已退款单数（统计区间内 status=4 已退款）
        Long refundAmount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM mall_refund WHERE status = 4 AND deleted = 0 AND create_time >= ?",
                Long.class, start);
        Long refundOrderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_refund WHERE status = 4 AND deleted = 0 AND create_time >= ?",
                Long.class, start);
        refundAmount = refundAmount == null ? 0L : refundAmount;
        refundOrderCount = refundOrderCount == null ? 0L : refundOrderCount;
        vo.setRefundAmount(refundAmount);
        vo.setRefundRate(orderCount > 0
                ? BigDecimal.valueOf(refundOrderCount).divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP).doubleValue()
                : 0.0);

        // 待办（全量 backlog，不随统计区间变化）
        Long pendingShipment = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_order WHERE status = 20 AND deleted = 0", Long.class);
        Long pendingRefundAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_refund WHERE status = 1 AND deleted = 0", Long.class);
        vo.setPendingShipment(pendingShipment == null ? 0L : pendingShipment);
        vo.setPendingRefundAudit(pendingRefundAudit == null ? 0L : pendingRefundAudit);

        // 新增会员（统计区间内注册）
        Long newMembers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mall_member WHERE deleted = 0 AND create_time >= ?", Long.class, start);
        vo.setNewMembers(newMembers == null ? 0L : newMembers);

        // Top5 商品（按成交量降序，仅统计区间内已支付订单的明细）
        List<MallDashboardVO.TopGoodsItem> top = jdbcTemplate.query(
                "SELECT oi.goods_id AS goodsId, oi.goods_name AS goodsName, "
                        + "SUM(oi.quantity) AS quantity, COALESCE(SUM(oi.total_amount), 0) AS amount "
                        + "FROM mall_order_item oi JOIN mall_order o ON oi.order_id = o.id "
                        + "WHERE o.pay_status = 1 AND o.deleted = 0 AND o.create_time >= ? "
                        + "GROUP BY oi.goods_id, oi.goods_name ORDER BY quantity DESC LIMIT 5",
                (rs, i) -> {
                    MallDashboardVO.TopGoodsItem item = new MallDashboardVO.TopGoodsItem();
                    item.setGoodsId(rs.getLong("goodsId"));
                    item.setGoodsName(rs.getString("goodsName"));
                    item.setQuantity(rs.getLong("quantity"));
                    item.setAmount(rs.getLong("amount"));
                    return item;
                }, start);
        vo.setTopGoods(top);

        return vo;
    }

    private String normalize(String range) {
        if (range == null) return "30d";
        return switch (range) {
            case "today", "7d", "30d" -> range;
            default -> "30d";
        };
    }

    private LocalDateTime rangeStart(String range) {
        LocalDateTime now = LocalDateTime.now();
        return switch (range) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> now.minusDays(30);
        };
    }
}
