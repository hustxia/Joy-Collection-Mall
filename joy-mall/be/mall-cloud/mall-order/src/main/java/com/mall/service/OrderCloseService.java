package com.mall.service;

import com.mall.entity.MallOrderLog;
import com.mall.service.MallOrderLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 超时关单（交易自愈）：扫描「待付款 + 超过超时时长 + 未删除」的订单，条件关单并回补库存。
 *
 * <p>关单状态迁移走条件 UPDATE（WHERE status=10），与 C 端 {@code /shop/order/cancel} 共用同一幂等闸门：
 * 同一单被定时任务与手动取消同时命中时，只有一个 affected=1，另一个 affected=0 跳过，库存不双重回补。
 * 单批上限配置化，循环小事务而非一次大事务，避免长事务锁表。
 */
@Service
public class OrderCloseService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderStockBackfillService backfillService;
    private final MallOrderLogService orderLogService;

    public OrderCloseService(JdbcTemplate jdbcTemplate,
                            OrderStockBackfillService backfillService,
                            MallOrderLogService orderLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.backfillService = backfillService;
        this.orderLogService = orderLogService;
    }

    /**
     * 关闭超时未支付订单。
     *
     * @param timeoutMinutes 超时时长（分钟），订单 create_time 早于此值视为超时
     * @param batchSize      单批上限，防止一次扫出过多行
     * @return 本批实际关闭的订单数（已幂等去重）
     */
    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredOrders(int timeoutMinutes, int batchSize) {
        List<OrderIdNo> candidates = jdbcTemplate.query(
                "SELECT id, order_no FROM mall_order"
                        + " WHERE status = 10 AND create_time < NOW() - INTERVAL ? MINUTE AND deleted = 0"
                        + " LIMIT ?",
                (rs, i) -> new OrderIdNo(rs.getLong("id"), rs.getString("order_no")),
                timeoutMinutes, batchSize);

        int closed = 0;
        for (OrderIdNo o : candidates) {
            // 条件关单：仅 status=10 可关；affected=0 说明已被并发取消/关闭，跳过（幂等）
            int affected = jdbcTemplate.update(
                    "UPDATE mall_order SET status = 60, close_time = NOW()"
                            + " WHERE id = ? AND status = 10 AND deleted = 0",
                    o.id());
            if (affected == 0) {
                continue;
            }
            // 回补 SKU 库存（复用共享方法）
            backfillService.backfill(List.of(o.id()));
            // 系统日志：operator_type=3
            MallOrderLog log = new MallOrderLog();
            log.setOrderId(o.id());
            log.setOrderNo(o.orderNo());
            log.setContent("超时未支付自动关闭，库存已回补");
            log.setOperator("系统");
            log.setOperatorType(3);
            orderLogService.save(log);
            closed++;
        }
        return closed;
    }

    /**
     * 后台关闭订单（20 待发货 → 60 已关闭）。
     *
     * <p>与超时关单共用条件 UPDATE 闸门与库存回补。已付款但未发货的订单关闭只回补库存、
     * <b>不自动退款</b>——退款走既有售后流程，避免「关单退款」与「售后退款」两套金额口径分叉。
     *
     * @param orderId  订单 ID
     * @param operator 操作人（管理员账号）
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeByAdmin(long orderId, String operator) {
        List<OrderIdNo> rows = jdbcTemplate.query(
                "SELECT id, order_no FROM mall_order WHERE id = ? AND status = 20 AND deleted = 0",
                (rs, i) -> new OrderIdNo(rs.getLong("id"), rs.getString("order_no")),
                orderId);
        if (rows.isEmpty()) {
            throw new com.mall.common.exception.BusinessException(400, "仅待发货订单可关闭，请刷新后重试");
        }
        // 条件关单：即使上面查到，也可能被并发关掉（affected=0 即失败，库存不回补两次）
        int affected = jdbcTemplate.update(
                "UPDATE mall_order SET status = 60, close_time = NOW()"
                        + " WHERE id = ? AND status = 20 AND deleted = 0",
                orderId);
        if (affected == 0) {
            throw new com.mall.common.exception.BusinessException(400, "仅待发货订单可关闭，请刷新后重试");
        }
        backfillService.backfill(List.of(orderId));
        MallOrderLog log = new MallOrderLog();
        log.setOrderId(rows.get(0).id());
        log.setOrderNo(rows.get(0).orderNo());
        log.setContent("管理员关闭订单，库存已回补");
        log.setOperator(operator);
        log.setOperatorType(2);
        orderLogService.save(log);
    }

    /** 候选订单：ID + 订单号 */
    public record OrderIdNo(long id, String orderNo) {
    }
}
