package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.OrderLogAppendDTO;
import com.mall.entity.MallRefund;
import com.mall.mapper.MallRefundMapper;
import com.mall.service.MallRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后退款单 Service
 *
 * <p>跨域访问一律走 Feign：订单信息/明细/日志走 {@link OrderInnerClient}，
 * 退货退款回补库存与回减销量走 {@link GoodsInnerClient}；本服务只直连自己的 mall_refund。
 */
@Slf4j
@Service
public class MallRefundServiceImpl extends ServiceImpl<MallRefundMapper, MallRefund> implements MallRefundService {

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    /** 状态机口径（与 docs/design/售后闭环-设计.md 2.1 节同源） */
    public static final int ST_PENDING = 1;
    public static final int ST_AGREED = 2;
    public static final int ST_REJECTED = 3;
    public static final int ST_REFUNDED = 4;
    public static final int ST_CLOSED = 5;

    public static final int TYPE_ONLY_REFUND = 1;
    public static final int TYPE_RETURN_REFUND = 2;
    public static final int TYPE_EXCHANGE = 3;
    public static final int TYPE_PARTIAL = 4;

    /** V15 换货扩展状态：2 已同意（待买家寄回）→ 6 → 7 → 8 终态 */
    public static final int ST_SHIPPED_BACK = 6;
    public static final int ST_RECEIVED = 7;
    public static final int ST_EXCHANGED = 8;

    /** V15 平台介入：0无 1仲裁中 2已强制退款 3已驳回 */
    public static final int PLAT_NONE = 0;
    public static final int PLAT_ARBITRATING = 1;
    public static final int PLAT_FORCED_REFUND = 2;
    public static final int PLAT_REJECTED = 3;

    /** V15 平台介入门槛：商家 48 小时未处理可申请仲裁（惰性判定，不做定时任务） */
    public static final int ARBITRATION_HOURS = 48;

    /** 进行中状态：占用订单，禁止重复申请 */
    private static final List<Integer> ACTIVE_STATUSES = List.of(ST_PENDING, ST_AGREED, ST_REFUNDED);
    /** 整单类售后（1/2/3）进行中即互斥；type=4 部分退款一单可并存多条 */
    private static final List<Integer> FULL_TYPES = List.of(TYPE_ONLY_REFUND, TYPE_RETURN_REFUND, TYPE_EXCHANGE);
    /** 可申请售后的订单状态：已发货 / 已完成 */
    private static final List<Integer> APPLICABLE_ORDER_STATUSES = List.of(30, 40);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private OrderInnerClient orderInnerClient;
    @Autowired
    private GoodsInnerClient goodsInnerClient;
    @Autowired
    private MerchantInnerClient merchantInnerClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public boolean canApply(Long memberId, Long orderId) {
        OrderBrief order = orderInnerClient.order(orderId);
        if (order == null || !memberId.equals(order.getMemberId())) {
            return false;
        }
        if (!APPLICABLE_ORDER_STATUSES.contains(order.getStatus())) {
            return false;
        }
        return countActiveFull(orderId) == 0;
    }

    /**
     * V15 口径拆分：整单类售后（1/2/3）互斥；type=4 部分退款可与部分退款并存、
     * 但与整单类售后互斥（整单退款会把已部分退掉的金额重复退）。
     */
    private long countActiveFull(Long orderId) {
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getOrderId, orderId)
                .in(MallRefund::getType, FULL_TYPES)
                .in(MallRefund::getStatus, ACTIVE_STATUSES);
        return count(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallRefund apply(Long memberId, String memberNickname, Long orderId, Integer type,
                            String reason, List<String> evidenceImages) {
        if (type == null || (type != TYPE_ONLY_REFUND && type != TYPE_RETURN_REFUND)) {
            throw new BusinessException(400, "售后类型不合法");
        }
        if (!StringUtils.hasText(reason) || reason.trim().isEmpty()) {
            throw new BusinessException(400, "请填写售后原因");
        }
        if (reason.length() > 200) {
            throw new BusinessException(400, "售后原因不能超过 200 字");
        }
        if (evidenceImages != null && evidenceImages.size() > 6) {
            throw new BusinessException(400, "最多上传 6 张凭证图");
        }
        OrderBrief order = orderInnerClient.order(orderId);
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (!APPLICABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(400, "该订单当前状态不支持申请售后");
        }
        // 整单类售后（1/2/3）互斥：已有进行中整单售后则拒绝
        if (countActiveFull(orderId) > 0) {
            throw new BusinessException(400, "该订单已有进行中的售后申请");
        }
        // V15：整单退款金额=实付，若已有部分退款（进行中或已退）会累计超实付，引导按订单项继续部分退款
        if (countPartialMoney(orderId) > 0) {
            throw new BusinessException(400, "该订单已有部分退款，请按订单项继续申请部分退款");
        }

        MallRefund refund = new MallRefund();
        refund.setRefundNo("RF" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        refund.setOrderId(orderId);
        refund.setOrderNo(order.getOrderNo());
        refund.setMemberId(memberId);
        refund.setType(type);
        // 金额只认服务端口径：订单实付（含券后），不支持部分退款
        refund.setAmount(order.getPayAmount());
        refund.setReason(reason.trim());
        refund.setEvidenceImages(toJson(evidenceImages));
        refund.setStatus(ST_PENDING);
        refund.setPayOrderNo(order.getPayOrderNo());
        save(refund);
        writeLog(order, "提交售后申请（" + typeText(type) + "，金额 " + fenToYuan(order.getPayAmount())
                + " 元），售后单号 " + refund.getRefundNo(), memberNickname, 1);
        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean agree, String auditRemark, String operator) {
        if (!StringUtils.hasText(auditRemark) && !agree) {
            throw new BusinessException(400, "拒绝时必须填写审核意见");
        }
        String remark = StringUtils.hasText(auditRemark) ? auditRemark.trim() : null;
        if (remark != null && remark.length() > 200) {
            throw new BusinessException(400, "审核意见不能超过 200 字");
        }
        int target = agree ? ST_AGREED : ST_REJECTED;
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET status = ?, audit_remark = ?"
                        + " WHERE id = ? AND status = ? AND deleted = 0",
                target, remark, id, ST_PENDING);
        if (affected == 0) {
            throw new BusinessException(400, "仅待审核的售后单可审核，请刷新后重试");
        }
        MallRefund refund = getById(id);
        OrderBrief order = orderInnerClient.order(refund.getOrderId());
        writeLog(order, agree ? "售后审核通过：" + (remark == null ? "" : remark)
                : "售后被拒绝：" + remark, operator, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallRefund doRefund(Long id, String operator) {
        String flowNo = "REFUND" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        // 条件 UPDATE 是幂等闸门：重复请求 status 已是 4，affected=0 直接拒绝，库存不会二次回补
        // V12：渠道退款字段同事务回填（channel_refund_no 即本次退款流水，对账依据）
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET status = ?, refund_time = NOW(),"
                        + " channel = IFNULL(channel, 'sandbox'), channel_refund_no = ?, refund_status = 2"
                        + " WHERE id = ? AND status = ? AND deleted = 0",
                ST_REFUNDED, flowNo, id, ST_AGREED);
        if (affected == 0) {
            throw new BusinessException(400, "仅已同意的售后单可退款，请刷新后重试");
        }
        MallRefund refund = getById(id);
        // pay_order_no 保留订单原支付单号（对账），退款流水号落 remark
        jdbcTemplate.update("UPDATE mall_refund SET remark = ? WHERE id = ?", flowNo, id);
        refund.setRemark(flowNo);
        refund.setRefundTime(LocalDateTime.now());

        // 积分 P0 口径：退款只回补库存、不扣回已发放的消费积分（扣回进 P2 F-06，需按退款金额折算）
        if (TYPE_RETURN_REFUND == refund.getType()) {
            // 退货退款：货已回库 → 回补库存并回减销量；仅退款不动库存
            List<OrderItemBrief> items = orderInnerClient.orderItems(refund.getOrderId());
            Map<Long, Integer> skuQty = new LinkedHashMap<>();
            Map<Long, Integer> goodsQty = new LinkedHashMap<>();
            for (OrderItemBrief item : items) {
                skuQty.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
                goodsQty.merge(item.getGoodsId(), item.getQuantity(), Integer::sum);
            }
            if (!skuQty.isEmpty()) {
                goodsInnerClient.restoreStock(skuQty);
                goodsInnerClient.incrementSkuSales(negate(skuQty));
            }
            if (!goodsQty.isEmpty()) {
                goodsInnerClient.incrementGoodsSales(negate(goodsQty));
            }
        }
        OrderBrief order = orderInnerClient.order(refund.getOrderId());
        // V10：退款完成 → 冲减商家应收与平台抽成（按 refundId 幂等；失败只告警，不回滚退款）
        adjustMerchant(order, refund);
        // V12：已开票的订单退款 → 原发票自动红冲（无票/已红冲静默跳过，不影响退款结果）
        try {
            Boolean flushed = orderInnerClient.redFlushInvoice(refund.getOrderId(), "订单退款自动红冲");
            if (Boolean.TRUE.equals(flushed)) {
                log.info("订单 {} 退款完成，发票已自动红冲", refund.getOrderNo());
            }
        } catch (Exception e) {
            log.warn("发票自动红冲失败（已降级，需人工处理），订单 {}：{}", refund.getOrderNo(), e.getMessage());
        }
        writeLog(order, "售后退款完成，金额 " + fenToYuan(refund.getAmount()) + " 元（mock 渠道），流水号 "
                + flowNo + (TYPE_RETURN_REFUND == refund.getType() ? "，库存已回补" : ""), operator, 2);
        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long refundId, Long memberId, String memberNickname) {
        MallRefund refund = getById(refundId);
        if (refund == null) {
            throw new BusinessException(400, "售后单不存在");
        }
        if (!memberId.equals(refund.getMemberId())) {
            throw new BusinessException(403, "无权操作该售后单");
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET status = ?"
                        + " WHERE id = ? AND status IN (?, ?) AND deleted = 0",
                ST_CLOSED, refundId, ST_PENDING, ST_AGREED);
        if (affected == 0) {
            throw new BusinessException(400, "当前状态不支持撤销");
        }
        OrderBrief order = orderInnerClient.order(refund.getOrderId());
        writeLog(order, "用户撤销售后，售后单号 " + refund.getRefundNo(), memberNickname, 1);
    }

    @Override
    public List<MallRefund> listLatestByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MallRefund::getOrderId, orderIds).orderByAsc(MallRefund::getId);
        // 一单可有多条历史，按 id 升序覆盖写入 → 每个 orderId 保留最新一条
        Map<Long, MallRefund> latest = new LinkedHashMap<>();
        for (MallRefund r : list(wrapper)) {
            latest.put(r.getOrderId(), r);
        }
        return new ArrayList<>(latest.values());
    }

    @Override
    public MallRefund latestByOrderId(Long orderId) {
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getOrderId, orderId).orderByDesc(MallRefund::getId).last("LIMIT 1");
        return getOne(wrapper);
    }

    /** 该订单进行中的售后单数（1/2/4 视为进行中；3/5 为终态可重新申请） */
    private long countActive(Long orderId) {
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getOrderId, orderId).in(MallRefund::getStatus, ACTIVE_STATUSES);
        return count(wrapper);
    }

    /** V15：该订单部分退款（type=4）已申请/已退金额合计（分），含进行中 1/2 与终态 4 */
    private long countPartialMoney(Long orderId) {
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getOrderId, orderId).eq(MallRefund::getType, TYPE_PARTIAL)
                .in(MallRefund::getStatus, ACTIVE_STATUSES);
        return list(wrapper).stream().mapToLong(r -> r.getAmount() == null ? 0L : r.getAmount()).sum();
    }

    /** V10 退款冲减：向商家域回退商家应收，幂等由商家域保证 */
    private void adjustMerchant(OrderBrief order, MallRefund refund) {
        if (order == null || order.getMerchantId() == null) {
            return;
        }
        try {
            Boolean done = merchantInnerClient.refundAdjust(refund.getId(), order.getId(),
                    order.getMerchantId(), refund.getAmount());
            if (Boolean.TRUE.equals(done)) {
                writeLog(order, "商家应收已按退款比例冲减（商家ID " + order.getMerchantId() + "）", "system", 3);
            }
        } catch (Exception e) {
            log.warn("商家退款冲减失败（已降级，待补冲减），售后单 {}：{}", refund.getId(), e.getMessage());
        }
    }

    private void writeLog(OrderBrief order, String content, String operator, int operatorType) {
        if (order == null) {
            return;
        }
        OrderLogAppendDTO dto = new OrderLogAppendDTO();
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setContent(content);
        dto.setOperator(operator);
        dto.setOperatorType(operatorType);
        orderInnerClient.appendLog(dto);
    }

    /** {id: qty} 取负，用于销量回减 */
    private Map<Long, Integer> negate(Map<Long, Integer> src) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, -v));
        return out;
    }

    private String toJson(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(urls);
        } catch (Exception e) {
            throw new BusinessException(400, "凭证图数据不合法");
        }
    }

    public static String typeText(Integer type) {
        if (TYPE_RETURN_REFUND == type) {
            return "退货退款";
        }
        if (TYPE_EXCHANGE == type) {
            return "换货";
        }
        if (TYPE_PARTIAL == type) {
            return "部分退款";
        }
        return TYPE_ONLY_REFUND == type ? "仅退款" : "未知";
    }

    public static String fenToYuan(Long fen) {
        if (fen == null) {
            return "0";
        }
        return String.format("%.2f", fen / 100.0);
    }

    /**
     * 自动退款（V12）：订单关闭后仍收到渠道成功回调 → 落一条终态退款单。
     * <p>不回补库存：关单/取消时已经回补过，这里再补会造成库存虚增。
     * <p>幂等：同订单已有 status=4 的退款单直接返回，避免重复回调刷出多条退款。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallRefund autoRefund(com.mall.common.feign.dto.RefundAutoRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException(400, "自动退款参数不完整");
        }
        MallRefund existing = getOne(new LambdaQueryWrapper<MallRefund>()
                .eq(MallRefund::getOrderId, request.getOrderId())
                .eq(MallRefund::getStatus, ST_REFUNDED)
                .orderByDesc(MallRefund::getId)
                .last("LIMIT 1"), false);
        if (existing != null) {
            log.info("订单 {} 已存在终态退款单 {}，自动退款幂等跳过",
                    request.getOrderNo(), existing.getRefundNo());
            return existing;
        }
        MallRefund refund = new MallRefund();
        refund.setRefundNo("RF" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        refund.setOrderId(request.getOrderId());
        refund.setOrderNo(request.getOrderNo());
        refund.setMemberId(request.getMemberId());
        refund.setType(TYPE_ONLY_REFUND);
        refund.setAmount(request.getAmount() == null ? 0L : request.getAmount());
        refund.setReason(StringUtils.hasText(request.getReason()) ? request.getReason()
                : "订单已关闭后收到支付成功回调，系统自动原路退款");
        refund.setStatus(ST_REFUNDED);
        refund.setRefundTime(LocalDateTime.now());
        refund.setPayOrderNo(request.getChannelTradeNo());
        refund.setChannel(request.getChannel());
        refund.setChannelRefundNo("AUTO" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        refund.setRefundStatus(2);
        save(refund);
        log.warn("检测到已关闭订单收到支付成功回调，已自动退款：orderNo={}, amount={}, refundNo={}",
                request.getOrderNo(), refund.getAmount(), refund.getRefundNo());
        try {
            OrderBrief order = orderInnerClient.order(request.getOrderId());
            writeLog(order, "订单关闭后收到支付成功回调，系统自动原路退款 " + fenToYuan(refund.getAmount())
                    + " 元，退款单号 " + refund.getRefundNo(), "system", 2);
        } catch (Exception e) {
            log.warn("自动退款写订单日志失败（已降级）：{}", e.getMessage());
        }
        return refund;
    }

    // ===== V15 售后扩展（REQ-P1-06）：换货 / 部分退款 / 平台介入 =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallRefund applyExchange(Long memberId, String memberNickname, Long orderId, Long orderItemId,
                                    Long exchangeSkuId, Integer quantity, String reason, List<String> evidenceImages) {
        if (!StringUtils.hasText(reason) || reason.trim().isEmpty()) {
            throw new BusinessException(400, "请填写换货原因");
        }
        if (reason.length() > 200) {
            throw new BusinessException(400, "换货原因不能超过 200 字");
        }
        if (exchangeSkuId == null) {
            throw new BusinessException(400, "请选择换入商品规格");
        }
        OrderBrief order = requireOwnOrder(memberId, orderId);
        // 订单项校验：必须属于该订单
        OrderItemBrief item = requireOrderItem(order, orderItemId);
        int qty = quantity == null ? 1 : quantity;
        if (qty < 1 || qty > item.getQuantity()) {
            throw new BusinessException(400, "换货数量必须在 1 ~ " + item.getQuantity() + " 之间");
        }
        // 换入 SKU 必须真实存在（经商品域校验；服务不可用会走 fallback 抛 503，绝不放行脏数据）
        if (goodsInnerClient.skuByIds(List.of(exchangeSkuId)).isEmpty()) {
            throw new BusinessException(400, "换入商品规格不存在");
        }
        if (countActiveFull(orderId) > 0) {
            throw new BusinessException(400, "该订单已有进行中的售后申请");
        }
        MallRefund refund = new MallRefund();
        refund.setRefundNo("RF" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        refund.setOrderId(orderId);
        refund.setOrderNo(order.getOrderNo());
        refund.setMemberId(memberId);
        refund.setType(TYPE_EXCHANGE);
        refund.setOrderItemId(orderItemId);
        refund.setQuantity(qty);
        refund.setExchangeSkuId(exchangeSkuId);
        refund.setAmount(0L);
        refund.setReason(reason.trim());
        refund.setEvidenceImages(toJson(evidenceImages));
        refund.setStatus(ST_PENDING);
        refund.setPayOrderNo(order.getPayOrderNo());
        save(refund);
        writeLog(order, "提交换货申请（换入SKU " + exchangeSkuId + " × " + qty + "），售后单号 "
                + refund.getRefundNo(), memberNickname, 1);
        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallRefund applyPartial(Long memberId, String memberNickname, Long orderId, Long orderItemId,
                                   Long amount, String reason, List<String> evidenceImages) {
        if (!StringUtils.hasText(reason) || reason.trim().isEmpty()) {
            throw new BusinessException(400, "请填写退款原因");
        }
        if (reason.length() > 200) {
            throw new BusinessException(400, "退款原因不能超过 200 字");
        }
        if (amount == null || amount <= 0) {
            throw new BusinessException(400, "退款金额必须大于 0");
        }
        OrderBrief order = requireOwnOrder(memberId, orderId);
        OrderItemBrief item = requireOrderItem(order, orderItemId);
        if (amount > item.getTotalAmount()) {
            throw new BusinessException(400, "退款金额不能超过该订单项实付 " + fenToYuan(item.getTotalAmount()) + " 元");
        }
        if (countActiveFull(orderId) > 0) {
            throw new BusinessException(400, "该订单已有进行中的整单售后申请");
        }
        // 累计口径：进行中（1/2）+ 已退（4）的部分退款 + 本次 ≤ 订单实付，超了直接 400
        long cumulative = countPartialMoney(orderId);
        if (cumulative + amount > order.getPayAmount()) {
            throw new BusinessException(400, "累计退款金额将超过订单实付（已申请/已退 "
                    + fenToYuan(cumulative) + " 元，实付 " + fenToYuan(order.getPayAmount()) + " 元）");
        }
        MallRefund refund = new MallRefund();
        refund.setRefundNo("RF" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        refund.setOrderId(orderId);
        refund.setOrderNo(order.getOrderNo());
        refund.setMemberId(memberId);
        refund.setType(TYPE_PARTIAL);
        refund.setOrderItemId(orderItemId);
        refund.setAmount(amount);
        refund.setReason(reason.trim());
        refund.setEvidenceImages(toJson(evidenceImages));
        refund.setStatus(ST_PENDING);
        refund.setPayOrderNo(order.getPayOrderNo());
        save(refund);
        writeLog(order, "提交部分退款申请（订单项 " + orderItemId + "，金额 " + fenToYuan(amount)
                + " 元），售后单号 " + refund.getRefundNo(), memberNickname, 1);
        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipBack(Long refundId, Long memberId, String memberNickname) {
        MallRefund refund = getById(refundId);
        if (refund == null) {
            throw new BusinessException(400, "售后单不存在");
        }
        if (!memberId.equals(refund.getMemberId())) {
            throw new BusinessException(403, "无权操作该售后单");
        }
        if (TYPE_EXCHANGE != refund.getType()) {
            throw new BusinessException(400, "仅换货单需要寄回商品");
        }
        // 条件 UPDATE 幂等闸门：重复寄回 affected=0
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET status = ?, ship_time = NOW()"
                        + " WHERE id = ? AND status = ? AND type = ? AND deleted = 0",
                ST_SHIPPED_BACK, refundId, ST_AGREED, TYPE_EXCHANGE);
        if (affected == 0) {
            throw new BusinessException(400, "仅审核通过（待寄回）的换货单可操作寄回");
        }
        writeLog(orderInnerClient.order(refund.getOrderId()),
                "买家已寄回商品，等待商家收货确认，售后单号 " + refund.getRefundNo(), memberNickname, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(Long id, String operator) {
        // 条件 UPDATE 是幂等闸门：重复确认 affected=0，库存不会二次回补
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET status = ? WHERE id = ? AND status = ? AND type = ? AND deleted = 0",
                ST_RECEIVED, id, ST_SHIPPED_BACK, TYPE_EXCHANGE);
        if (affected == 0) {
            throw new BusinessException(400, "仅「买家已寄回」的换货单可收货确认");
        }
        MallRefund refund = getById(id);
        // 库存口径（REQ-P1-06）：此时才收到退货 → 回补换出 SKU；换入 SKU 待发出换货时扣减
        OrderItemBrief item = orderItemOf(refund);
        int qty = refund.getQuantity() == null ? 1 : refund.getQuantity();
        goodsInnerClient.restoreStock(Map.of(item.getSkuId(), qty));
        writeLog(orderInnerClient.order(refund.getOrderId()),
                "商家已收货确认，换出商品库存已回补（SKU " + item.getSkuId() + " +" + qty + "）", operator, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendExchange(Long id, String operator) {
        MallRefund refund = getById(id);
        if (refund == null || TYPE_EXCHANGE != refund.getType()) {
            throw new BusinessException(400, "换货单不存在");
        }
        OrderItemBrief item = orderItemOf(refund);
        Long exchangeSkuId = refund.getExchangeSkuId();
        int qty = refund.getQuantity() == null ? 1 : refund.getQuantity();
        // 条件 UPDATE 是幂等闸门：重复发出 affected=0，换入库存不会二次扣减
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET status = ? WHERE id = ? AND status = ? AND type = ? AND deleted = 0",
                ST_EXCHANGED, id, ST_RECEIVED, TYPE_EXCHANGE);
        if (affected == 0) {
            throw new BusinessException(400, "仅「商家已收货」的换货单可发出换货");
        }
        // 换入 SKU 出库：库存不足直接 400（事务回滚，状态不会推进）
        Boolean deducted = goodsInnerClient.deductStock(Map.of(exchangeSkuId, qty));
        if (!Boolean.TRUE.equals(deducted)) {
            throw new BusinessException(400, "换入商品库存不足，无法发出换货");
        }
        writeLog(orderInnerClient.order(refund.getOrderId()),
                "商家已发出换货（换入SKU " + exchangeSkuId + " × " + qty + "，库存 -" + qty + "），换货完成",
                operator, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyIntervene(Long refundId, Long memberId, String memberNickname) {
        MallRefund refund = getById(refundId);
        if (refund == null) {
            throw new BusinessException(400, "售后单不存在");
        }
        if (!memberId.equals(refund.getMemberId())) {
            throw new BusinessException(403, "无权操作该售后单");
        }
        if (ST_PENDING != refund.getStatus()) {
            throw new BusinessException(400, "仅待审核的售后单可申请平台介入");
        }
        if (refund.getCreateTime() == null
                || refund.getCreateTime().isAfter(LocalDateTime.now().minusHours(ARBITRATION_HOURS))) {
            throw new BusinessException(400, "商家 48 小时内未处理才可申请平台介入，请耐心等待");
        }
        // 条件 UPDATE 幂等闸门：重复介入 affected=0
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET platform_status = ?, intervene_time = NOW()"
                        + " WHERE id = ? AND status = ? AND platform_status = ? AND deleted = 0",
                PLAT_ARBITRATING, refundId, ST_PENDING, PLAT_NONE);
        if (affected == 0) {
            throw new BusinessException(400, "该售后单已申请过平台介入");
        }
        writeLog(orderInnerClient.order(refund.getOrderId()),
                "商家超 48 小时未处理，买家申请平台介入，售后单号 " + refund.getRefundNo(), memberNickname, 1);
    }

    @Override
    public Page<MallRefund> arbitrationPage(long pageNum, long pageSize) {
        // 惰性判定：status=1（商家待审核未处理）且 create_time 已超 48h 即入队，
        // 含买家已申请介入（platform_status in (0,1)）的工单；不依赖定时任务。
        LambdaQueryWrapper<MallRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallRefund::getStatus, ST_PENDING)
                .in(MallRefund::getPlatformStatus, PLAT_NONE, PLAT_ARBITRATING)
                .le(MallRefund::getCreateTime, LocalDateTime.now().minusHours(ARBITRATION_HOURS))
                .orderByAsc(MallRefund::getCreateTime);
        return page(new Page<>(pageNum, Math.min(pageSize, 100)), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallRefund arbitrate(Long id, boolean forceRefund, String remark, String operator) {
        String text = StringUtils.hasText(remark) ? remark.trim() : null;
        if (forceRefund && !StringUtils.hasText(text)) {
            throw new BusinessException(400, "强制退款必须填写仲裁意见");
        }
        if (text != null && text.length() > 200) {
            throw new BusinessException(400, "仲裁意见不能超过 200 字");
        }
        if (forceRefund) {
            // 强制退款：1→2（视同审核通过）+ platform_status=2，一条 UPDATE 完成闸门，随后复用退款主流程到 4 终态
            int affected = jdbcTemplate.update(
                    "UPDATE mall_refund SET status = ?, platform_status = ?, platform_remark = ?, audit_remark = ?"
                            + " WHERE id = ? AND status = ? AND platform_status IN (?, ?) AND deleted = 0",
                    ST_AGREED, PLAT_FORCED_REFUND, text, "平台仲裁强制退款：" + text, id, ST_PENDING, PLAT_NONE, PLAT_ARBITRATING);
            if (affected == 0) {
                throw new BusinessException(400, "仅待审核且未裁决的售后单可仲裁");
            }
            writeLog(orderInnerClient.order(getById(id).getOrderId()),
                    "平台仲裁：强制退款（" + text + "），售后单号 " + getById(id).getRefundNo(), operator, 3);
            return doRefund(id, operator + "(平台仲裁)");
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_refund SET platform_status = ?, platform_remark = ?"
                        + " WHERE id = ? AND status = ? AND platform_status IN (?, ?) AND deleted = 0",
                PLAT_REJECTED, text, id, ST_PENDING, PLAT_NONE, PLAT_ARBITRATING);
        if (affected == 0) {
            throw new BusinessException(400, "仅待审核且未裁决的售后单可仲裁");
        }
        writeLog(orderInnerClient.order(getById(id).getOrderId()),
                "平台仲裁：驳回买家申请" + (text == null ? "" : "（" + text + "）"), operator, 3);
        return getById(id);
    }

    /** 归属+状态校验：订单存在、属于本人、状态可售后（V15 换货/部分退款共用） */
    private OrderBrief requireOwnOrder(Long memberId, Long orderId) {
        OrderBrief order = orderInnerClient.order(orderId);
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (!APPLICABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(400, "该订单当前状态不支持申请售后");
        }
        return order;
    }

    /** 订单项校验：必须属于该订单 */
    private OrderItemBrief requireOrderItem(OrderBrief order, Long orderItemId) {
        if (orderItemId == null) {
            throw new BusinessException(400, "请选择订单项");
        }
        return orderInnerClient.orderItems(order.getId()).stream()
                .filter(i -> orderItemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "订单项不属于该订单"));
    }

    /** 换货单关联的订单项（收货/发出换货时定位换出 SKU） */
    private OrderItemBrief orderItemOf(MallRefund refund) {
        return orderInnerClient.orderItems(refund.getOrderId()).stream()
                .filter(i -> refund.getOrderItemId() != null && refund.getOrderItemId().equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "换货单关联的订单项不存在"));
    }
}
