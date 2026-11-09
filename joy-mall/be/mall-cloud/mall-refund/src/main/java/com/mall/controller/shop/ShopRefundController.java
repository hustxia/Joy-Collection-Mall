package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.result.Result;
import com.mall.entity.MallRefund;
import com.mall.service.MallRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端售后接口（V4 P0-1）。
 *
 * <p>前缀 /shop：AuthContextFilter 会员身份只放行 /shop/**（且本控制器全部端点
 * 都不在 SHOP_ANON_LIST，必须持会员 token——售后是会员私有数据，无匿名场景）。
 * 订单/明细/日志经订单域 /inner 接口读取，会员昵称经会员域 /inner 接口读取。
 */
@Tag(name = "商城前台-售后", description = "售后申请/查询/撤销")
@RestController
@RequestMapping("/shop/refund")
public class ShopRefundController {

    @Autowired
    private MallRefundService mallRefundService;
    @Autowired
    private OrderInnerClient orderInnerClient;
    @Autowired
    private MemberInnerClient memberInnerClient;

    @Operation(summary = "申请售后")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody ApplyDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "参数错误：orderId 必填");
        }
        Long memberId = UserContext.getUserId();
        MallRefund refund = mallRefundService.apply(memberId, nicknameOf(memberId),
                dto.getOrderId(), dto.getType(), dto.getReason(), dto.getEvidenceImages());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundId", String.valueOf(refund.getId()));
        data.put("refundNo", refund.getRefundNo());
        data.put("status", refund.getStatus());
        return Result.ok(data);
    }

    @Operation(summary = "我的售后分页（status 可选：1/2/3/4/5）")
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize) {
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getMemberId, UserContext.getUserId());
        if (status != null) {
            wrapper.eq(MallRefund::getStatus, status);
        }
        wrapper.orderByDesc(MallRefund::getId);
        Page<MallRefund> page = mallRefundService.page(new Page<>(pageNum, Math.min(pageSize, 50)), wrapper);
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(ShopRefundController::summary).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "售后详情：售后单+订单摘要+明细+时间线")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        MallRefund refund = mallRefundService.getById(id);
        if (refund == null) {
            throw new BusinessException(400, "售后单不存在");
        }
        if (!UserContext.getUserId().equals(refund.getMemberId())) {
            throw new BusinessException(403, "无权查看该售后单");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refund", refund);
        OrderBrief order = orderInnerClient.order(refund.getOrderId());
        Map<String, Object> orderBrief = new LinkedHashMap<>();
        if (order != null) {
            orderBrief.put("orderNo", order.getOrderNo());
            orderBrief.put("payAmount", order.getPayAmount());
            orderBrief.put("status", order.getStatus());
            orderBrief.put("receiverName", order.getReceiverName());
            orderBrief.put("receiverPhone", order.getReceiverPhone());
            orderBrief.put("receiverAddress", order.getReceiverAddress());
        }
        data.put("order", orderBrief);
        data.put("items", orderInnerClient.orderItems(refund.getOrderId()));
        data.put("timeline", orderInnerClient.orderLogs(refund.getOrderId()).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", l.getContent());
            m.put("operator", l.getOperator());
            m.put("operatorType", l.getOperatorType());
            m.put("createTime", l.getCreateTime());
            return m;
        }).toList());
        return Result.ok(data);
    }

    @Operation(summary = "撤销售后（仅待审核/已同意）")
    @PostMapping("/close")
    public Result<Void> close(@RequestBody RefundIdDTO dto) {
        if (dto == null || dto.getRefundId() == null) {
            throw new BusinessException(400, "参数错误：refundId 必填");
        }
        Long memberId = UserContext.getUserId();
        mallRefundService.close(dto.getRefundId(), memberId, nicknameOf(memberId));
        return Result.ok();
    }

    // ===== V15 售后扩展（REQ-P1-06）=====

    @Operation(summary = "申请换货（V15）：选订单项+换入SKU，库存不立即回补")
    @PostMapping("/apply/exchange")
    public Result<Map<String, Object>> applyExchange(@RequestBody ExchangeApplyDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "参数错误：orderId 必填");
        }
        Long memberId = UserContext.getUserId();
        MallRefund refund = mallRefundService.applyExchange(memberId, nicknameOf(memberId), dto.getOrderId(),
                dto.getOrderItemId(), dto.getExchangeSkuId(), dto.getQuantity(),
                dto.getReason(), dto.getEvidenceImages());
        return Result.ok(applyResult(refund));
    }

    @Operation(summary = "申请部分退款（V15）：按订单项选金额，累计不超过实付")
    @PostMapping("/apply/part")
    public Result<Map<String, Object>> applyPartial(@RequestBody PartialApplyDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            throw new BusinessException(400, "参数错误：orderId 必填");
        }
        Long memberId = UserContext.getUserId();
        MallRefund refund = mallRefundService.applyPartial(memberId, nicknameOf(memberId), dto.getOrderId(),
                dto.getOrderItemId(), dto.getAmount(), dto.getReason(), dto.getEvidenceImages());
        return Result.ok(applyResult(refund));
    }

    @Operation(summary = "买家寄回（V15 换货）：仅已同意（待寄回）可操作")
    @PostMapping("/ship")
    public Result<Void> shipBack(@RequestBody RefundIdDTO dto) {
        if (dto == null || dto.getRefundId() == null) {
            throw new BusinessException(400, "参数错误：refundId 必填");
        }
        Long memberId = UserContext.getUserId();
        mallRefundService.shipBack(dto.getRefundId(), memberId, nicknameOf(memberId));
        return Result.ok();
    }

    @Operation(summary = "申请平台介入（V15）：商家超 48 小时未处理可申请")
    @PostMapping("/intervene")
    public Result<Void> intervene(@RequestBody RefundIdDTO dto) {
        if (dto == null || dto.getRefundId() == null) {
            throw new BusinessException(400, "参数错误：refundId 必填");
        }
        Long memberId = UserContext.getUserId();
        mallRefundService.applyIntervene(dto.getRefundId(), memberId, nicknameOf(memberId));
        return Result.ok();
    }

    private static Map<String, Object> applyResult(MallRefund refund) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundId", String.valueOf(refund.getId()));
        data.put("refundNo", refund.getRefundNo());
        data.put("status", refund.getStatus());
        return data;
    }

    @Operation(summary = "订单可否申请售后（前端按钮显隐）")
    @GetMapping("/order/{orderId}")
    public Result<Map<String, Object>> orderRefundState(@PathVariable("orderId") Long orderId) {
        Long memberId = UserContext.getUserId();
        boolean canApply = mallRefundService.canApply(memberId, orderId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("canApply", canApply);
        if (!canApply) {
            OrderBrief order = orderInnerClient.order(orderId);
            if (order == null || !memberId.equals(order.getMemberId())) {
                data.put("reason", "订单不存在或无权访问");
            } else if (!List.of(30, 40).contains(order.getStatus())) {
                data.put("reason", "该订单当前状态不支持申请售后");
            } else {
                data.put("reason", "该订单已有进行中的售后申请");
            }
        }
        // 无论可否申请，只要该订单有过售后单，就下发最新一条摘要（订单详情页展示「售后进度」卡）
        MallRefund latest = latestRefund(orderId);
        if (latest != null) {
            data.put("refund", summary(latest));
        }
        return Result.ok(data);
    }

    /** 该订单最新一条售后单（含终态），无则 null */
    private MallRefund latestRefund(Long orderId) {
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getOrderId, orderId).orderByDesc(MallRefund::getId).last("LIMIT 1");
        return mallRefundService.getOne(wrapper);
    }

    /** 列表行摘要：敏感字段（member_id/evidence/audit_remark）不下发，金额与状态齐 */
    private static Map<String, Object> summary(MallRefund r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("refundNo", r.getRefundNo());
        m.put("orderId", String.valueOf(r.getOrderId()));
        m.put("orderNo", r.getOrderNo());
        m.put("type", r.getType());
        m.put("amount", r.getAmount());
        m.put("status", r.getStatus());
        m.put("reason", r.getReason());
        m.put("auditRemark", r.getAuditRemark());
        m.put("refundTime", r.getRefundTime());
        m.put("createTime", r.getCreateTime());
        // V15：换货/部分退款/平台介入进度字段
        m.put("orderItemId", r.getOrderItemId());
        m.put("quantity", r.getQuantity());
        m.put("exchangeSkuId", r.getExchangeSkuId());
        m.put("shipTime", r.getShipTime());
        m.put("platformStatus", r.getPlatformStatus());
        m.put("platformRemark", r.getPlatformRemark());
        m.put("interveneTime", r.getInterveneTime());
        return m;
    }

    private String nicknameOf(Long memberId) {
        MemberBrief member = memberInnerClient.member(memberId);
        return member != null && StringUtils.hasText(member.getNickname())
                ? member.getNickname() : String.valueOf(UserContext.getUsername());
    }

    public static class ApplyDTO {
        private Long orderId;
        private Integer type;
        private String reason;
        private List<String> evidenceImages;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Integer getType() { return type; }
        public void setType(Integer type) { this.type = type; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<String> getEvidenceImages() { return evidenceImages; }
        public void setEvidenceImages(List<String> evidenceImages) { this.evidenceImages = evidenceImages; }
    }

    public static class RefundIdDTO {
        private Long refundId;

        public Long getRefundId() { return refundId; }
        public void setRefundId(Long refundId) { this.refundId = refundId; }
    }

    /** V15 换货申请 */
    public static class ExchangeApplyDTO {
        private Long orderId;
        private Long orderItemId;
        private Long exchangeSkuId;
        private Integer quantity;
        private String reason;
        private List<String> evidenceImages;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Long getExchangeSkuId() { return exchangeSkuId; }
        public void setExchangeSkuId(Long exchangeSkuId) { this.exchangeSkuId = exchangeSkuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<String> getEvidenceImages() { return evidenceImages; }
        public void setEvidenceImages(List<String> evidenceImages) { this.evidenceImages = evidenceImages; }
    }

    /** V15 部分退款申请（按订单项选金额） */
    public static class PartialApplyDTO {
        private Long orderId;
        private Long orderItemId;
        private Long amount;
        private String reason;
        private List<String> evidenceImages;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<String> getEvidenceImages() { return evidenceImages; }
        public void setEvidenceImages(List<String> evidenceImages) { this.evidenceImages = evidenceImages; }
    }
}
