package com.mall.ai.support;

import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.RefundInnerClient;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.RefundBrief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具网关：AI 唯一的数据入口，<b>全部只读</b>。
 *
 * <p>设计要点：
 * <ul>
 *   <li>只代理现有 {@code /inner} 契约，不新增查询逻辑，数据口径与页面一致；</li>
 *   <li>异常一律吞掉并记 warn——订单域抖动不能让客服不可用，退化为「查不到」走兜底；</li>
 *   <li>没有 write 方法。AI 想做写操作，物理上就没有这个能力。</li>
 * </ul>
 */
@Slf4j
@Component
public class ToolGateway {

    private final OrderInnerClient orderInnerClient;
    private final RefundInnerClient refundInnerClient;

    public ToolGateway(OrderInnerClient orderInnerClient, RefundInnerClient refundInnerClient) {
        this.orderInnerClient = orderInnerClient;
        this.refundInnerClient = refundInnerClient;
    }

    /** 按意图取证：带订单号优先按号查，否则取最近一单 */
    public Evidence collect(Intent intent, String orderNo, Long memberId) {
        Evidence evidence = new Evidence();
        if (intent != Intent.ORDER && intent != Intent.LOGISTICS && intent != Intent.REFUND && orderNo == null) {
            return evidence;
        }
        OrderBrief order = null;
        try {
            order = orderNo != null
                    ? orderInnerClient.orderByNo(orderNo, memberId)
                    : orderInnerClient.latestOrder(memberId);
        } catch (Exception e) {
            log.warn("order evidence failed: {}", e.getMessage());
            evidence.getWarn().add("order-service:" + e.getClass().getSimpleName());
        }
        if (order == null) {
            if (orderNo != null) {
                evidence.getWarn().add("order-not-found");
            }
            return evidence;
        }
        evidence.setOrder(order);

        try {
            List<OrderItemBrief> items = orderInnerClient.orderItems(order.getId());
            evidence.setItems(items == null ? List.of() : items);
        } catch (Exception e) {
            evidence.getWarn().add("order-item:" + e.getClass().getSimpleName());
        }

        if (order.getStatus() != null && order.getStatus() >= 30) {
            try {
                Map<String, Object> track = orderInnerClient.deliveryTrack(order.getId(), memberId);
                evidence.setTrack(track);
            } catch (Exception e) {
                evidence.getWarn().add("delivery:" + e.getClass().getSimpleName());
            }
        }
        try {
            RefundBrief refund = refundInnerClient.latestByOrderId(order.getId());
            evidence.setRefund(refund);
        } catch (Exception e) {
            evidence.getWarn().add("refund:" + e.getClass().getSimpleName());
        }
        return evidence;
    }
}
