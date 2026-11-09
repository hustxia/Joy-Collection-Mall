package com.mall.scheduler;

import com.mall.service.OrderCloseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订单定时关闭调度（交易自愈底线）。
 *
 * <p>每 5 分钟扫描一次（cron 可配置），调用 {@link OrderCloseService#closeExpiredOrders} 关单并回补库存。
 * 单实例部署，不加分布式锁：关单条件 UPDATE 幂等，并发安全由 SQL 行锁保证。
 */
@Component
public class OrderCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseScheduler.class);

    private final OrderCloseService orderCloseService;

    @Value("${mall.scheduler.order-close.timeout-minutes:30}")
    private int timeoutMinutes;

    @Value("${mall.scheduler.order-close.batch-size:200}")
    private int batchSize;

    @Value("${mall.scheduler.order-close.enabled:true}")
    private boolean enabled;

    public OrderCloseScheduler(OrderCloseService orderCloseService) {
        this.orderCloseService = orderCloseService;
    }

    @Scheduled(cron = "${mall.scheduler.order-close.cron:0 */5 * * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int closed = orderCloseService.closeExpiredOrders(timeoutMinutes, batchSize);
            if (closed > 0) {
                log.info("超时关单：本批自动关闭 {} 个未支付订单（超时时长 {} 分钟）", closed, timeoutMinutes);
            }
        } catch (Exception e) {
            // 调度任务异常不应影响主流程；记录后下一周期重试
            log.error("超时关单调度执行异常", e);
        }
    }
}
