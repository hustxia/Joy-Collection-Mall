package com.mall.controller.inner;

import com.mall.common.feign.dto.RefundAutoRequest;
import com.mall.common.feign.dto.RefundBrief;
import com.mall.entity.MallRefund;
import com.mall.service.MallRefundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 售后域内部端点（/inner/**，免鉴权）：供订单域列表/详情回填每个订单的最新售后单。
 *
 * <p>内部 RPC 直接返回原始载荷（不套 Result 壳）。
 */
@RestController
@RequestMapping("/inner")
public class InnerRefundController {

    private final MallRefundService mallRefundService;

    public InnerRefundController(MallRefundService mallRefundService) {
        this.mallRefundService = mallRefundService;
    }

    @GetMapping("/by-order-ids")
    public List<RefundBrief> latestByOrderIds(@RequestParam("ids") List<Long> orderIds) {
        return mallRefundService.listLatestByOrderIds(orderIds).stream().map(this::toBrief).toList();
    }

    @GetMapping("/latest-by-order/{orderId}")
    public RefundBrief latestByOrderId(@PathVariable("orderId") Long orderId) {
        MallRefund r = mallRefundService.latestByOrderId(orderId);
        return r == null ? null : toBrief(r);
    }

    /** V12 自动退款：订单关闭后收到支付成功回调时，由支付模块调用落终态退款单 */
    @PostMapping("/auto-refund")
    public RefundBrief autoRefund(@RequestBody RefundAutoRequest request) {
        return toBrief(mallRefundService.autoRefund(request));
    }

    private RefundBrief toBrief(MallRefund r) {
        RefundBrief b = new RefundBrief();
        b.setId(r.getId());
        b.setRefundNo(r.getRefundNo());
        b.setOrderId(r.getOrderId());
        b.setStatus(r.getStatus());
        b.setRefundTime(r.getRefundTime());
        return b;
    }
}
