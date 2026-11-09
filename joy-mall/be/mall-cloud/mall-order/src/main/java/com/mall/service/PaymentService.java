package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.RefundInnerClient;
import com.mall.common.feign.dto.RefundAutoRequest;
import com.mall.common.feign.dto.RefundBrief;
import com.mall.dto.PaymentPageDTO;
import com.mall.dto.ReconcileRunDTO;
import com.mall.entity.MallOrder;
import com.mall.entity.MallPayReconcile;
import com.mall.entity.MallPayment;
import com.mall.mapper.MallPayReconcileMapper;
import com.mall.mapper.MallPaymentMapper;
import com.mall.pay.PayChannel;
import com.mall.pay.PayChannelRegistry;
import com.mall.pay.PayNotify;
import com.mall.pay.SandboxPayChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务（V12）：支付单生命周期、回调幂等推进、关单后退款、对账。
 *
 * <p>状态机（{@code mall_payment.status}）：0 待支付 → 1 成功 → 3 全额退 / 4 部分退；0 → 2 失败。
 * 所有迁移走条件 UPDATE（WHERE status=0），回调重放天然幂等。
 *
 * <p>三个关键判定：
 * <ol>
 *   <li><b>金额必须一致</b>：回调金额 ≠ 支付单金额直接 400，不做「以渠道为准」的自动改正；</li>
 *   <li><b>关单后回调以退款收尾</b>：订单已是 50/60 却收到成功回调 → 自动原路退款，不复活订单；</li>
 *   <li><b>渠道失败不伪造成功</b>：失败回调落 2，前端可重新发起支付。</li>
 * </ol>
 */
@Slf4j
@Service
public class PaymentService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 订单状态：待付款 */
    private static final int ORDER_UNPAID = 10;
    /** 订单状态：已取消 */
    private static final int ORDER_CANCELED = 50;
    /** 订单状态：已关闭 */
    private static final int ORDER_CLOSED = 60;

    private final MallPaymentMapper paymentMapper;
    private final MallPayReconcileMapper reconcileMapper;
    private final MallOrderService mallOrderService;
    private final OrderPaidService orderPaidService;
    private final PayChannelRegistry channelRegistry;
    private final RefundInnerClient refundInnerClient;
    private final JdbcTemplate jdbcTemplate;

    public PaymentService(MallPaymentMapper paymentMapper, MallPayReconcileMapper reconcileMapper,
                          MallOrderService mallOrderService, OrderPaidService orderPaidService,
                          PayChannelRegistry channelRegistry, RefundInnerClient refundInnerClient,
                          JdbcTemplate jdbcTemplate) {
        this.paymentMapper = paymentMapper;
        this.reconcileMapper = reconcileMapper;
        this.mallOrderService = mallOrderService;
        this.orderPaidService = orderPaidService;
        this.channelRegistry = channelRegistry;
        this.refundInnerClient = refundInnerClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== 1. 下单取支付参数 =====

    /**
     * 预支付：校验订单归属与状态，复用未支付的旧支付单（半小时内），返回渠道参数。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> prepay(Long memberId, String orderNo, String channelCode, String tradeType) {
        MallOrder order = requireOwnOrder(memberId, orderNo);
        if (Integer.valueOf(1).equals(order.getPayStatus())
                || !Integer.valueOf(ORDER_UNPAID).equals(order.getStatus())) {
            throw new BusinessException(400, "订单当前状态不支持支付");
        }
        PayChannel channel = channelRegistry.require(channelCode);
        String type = tradeType == null || tradeType.isBlank() ? "NATIVE" : tradeType.trim().toUpperCase();

        MallPayment payment = findReusable(order.getId(), channel.code());
        if (payment == null) {
            payment = new MallPayment();
            payment.setPayNo("P" + LocalDateTime.now().format(TS)
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
            payment.setOrderId(order.getId());
            payment.setOrderNo(order.getOrderNo());
            payment.setMemberId(memberId);
            payment.setMerchantId(order.getMerchantId());
            payment.setChannel(channel.code());
            payment.setTradeType(type);
            payment.setAmount(order.getPayAmount() == null ? 0L : order.getPayAmount());
            payment.setStatus(MallPayment.ST_WAIT);
            payment.setExpireTime(LocalDateTime.now().plusMinutes(30));
            paymentMapper.insert(payment);
        }
        Map<String, Object> params = channel.prepay(payment);
        try {
            payment.setPrepayParams(JSON.writeValueAsString(params));
            paymentMapper.updateById(payment);
        } catch (Exception e) {
            log.warn("支付参数序列化失败（不影响下单）：{}", e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payNo", payment.getPayNo());
        data.put("orderNo", order.getOrderNo());
        data.put("amount", payment.getAmount());
        data.put("channel", payment.getChannel());
        data.put("tradeType", payment.getTradeType());
        data.put("expireTime", payment.getExpireTime());
        data.put("params", params);
        return data;
    }

    /** 复用规则：同订单同渠道、仍待支付、未过期 */
    private MallPayment findReusable(Long orderId, String channel) {
        List<MallPayment> list = paymentMapper.selectList(new LambdaQueryWrapper<MallPayment>()
                .eq(MallPayment::getOrderId, orderId)
                .eq(MallPayment::getChannel, channel)
                .eq(MallPayment::getStatus, MallPayment.ST_WAIT)
                .ge(MallPayment::getExpireTime, LocalDateTime.now())
                .orderByDesc(MallPayment::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    // ===== 2. 渠道回调 =====

    /**
     * 处理渠道回调（幂等）：验签 → 定位支付单 → 金额校验 → 条件更新 → 推进订单 / 关单退款。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> notify(String channelCode, String rawBody) {
        PayChannel channel = channelRegistry.require(channelCode);
        PayNotify notify = channel.verifyNotify(rawBody);
        MallPayment payment = paymentMapper.selectOne(new LambdaQueryWrapper<MallPayment>()
                .eq(MallPayment::getPayNo, notify.getPayNo()).last("LIMIT 1"));
        if (payment == null) {
            throw new BusinessException(400, "支付单不存在：" + notify.getPayNo());
        }
        // 已是终态：按幂等重放返回，绝不二次推进
        if (Integer.valueOf(MallPayment.ST_SUCCESS).equals(payment.getStatus())
                || Integer.valueOf(MallPayment.ST_REFUNDED).equals(payment.getStatus())
                || Integer.valueOf(MallPayment.ST_PART_REFUNDED).equals(payment.getStatus())) {
            return replayData(payment, true);
        }
        if (!notify.success()) {
            jdbcTemplate.update("UPDATE mall_payment SET status = ?, notify_time = NOW(), raw_notify = ?"
                    + " WHERE id = ? AND status = ?", MallPayment.ST_FAIL, notify.getRaw(),
                    payment.getId(), MallPayment.ST_WAIT);
            MallPayment failed = paymentMapper.selectById(payment.getId());
            return replayData(failed, false);
        }
        if (!Objects.equals(payment.getAmount(), notify.getAmount())) {
            log.error("支付回调金额不一致：payNo={}，本地 {}，渠道 {}", payment.getPayNo(),
                    payment.getAmount(), notify.getAmount());
            throw new BusinessException(400, "回调金额与支付单不一致");
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_payment SET status = ?, channel_trade_no = ?, notify_time = NOW(), raw_notify = ?"
                        + " WHERE id = ? AND status = ?",
                MallPayment.ST_SUCCESS, notify.getChannelTradeNo(), notify.getRaw(),
                payment.getId(), MallPayment.ST_WAIT);
        if (affected == 0) {
            // 并发下已被另一条回调处理：按重放返回
            return replayData(paymentMapper.selectById(payment.getId()), true);
        }
        MallPayment fresh = paymentMapper.selectById(payment.getId());
        MallOrder order = mallOrderService.getById(fresh.getOrderId());
        if (order == null) {
            throw new BusinessException(400, "支付单关联的订单不存在");
        }
        if (Integer.valueOf(ORDER_CLOSED).equals(order.getStatus())
                || Integer.valueOf(ORDER_CANCELED).equals(order.getStatus())) {
            // 关单后到账：不复活订单，自动原路退款（钱必须退回去）
            autoRefund(fresh, order);
            return replayData(paymentMapper.selectById(fresh.getId()), false);
        }
        boolean advanced = orderPaidService.advance(order, fresh.getPayNo(), payTypeOf(fresh.getChannel()));
        Map<String, Object> data = replayData(paymentMapper.selectById(fresh.getId()), !advanced);
        data.put("orderStatus", mallOrderService.getById(fresh.getOrderId()).getStatus());
        return data;
    }

    /** 关单后退款：落售后域终态退款单 + 支付单置已退；任何异常都不回滚已确认的收款 */
    private void autoRefund(MallPayment payment, MallOrder order) {
        RefundAutoRequest request = new RefundAutoRequest();
        request.setOrderId(order.getId());
        request.setOrderNo(order.getOrderNo());
        request.setMemberId(order.getMemberId());
        request.setAmount(payment.getAmount());
        request.setChannel(payment.getChannel());
        request.setChannelTradeNo(payment.getChannelTradeNo());
        request.setReason("订单已关闭后收到支付成功回调，系统自动原路退款");
        try {
            RefundBrief refund = refundInnerClient.autoRefund(request);
            jdbcTemplate.update("UPDATE mall_payment SET status = ? WHERE id = ?",
                    MallPayment.ST_REFUNDED, payment.getId());
            log.warn("关单后到账已自动退款：payNo={}, refundNo={}", payment.getPayNo(),
                    refund == null ? "-" : refund.getRefundNo());
        } catch (Exception e) {
            // 退款失败必须留痕：这是「钱收了货没发」的高危场景，对账会以长款形式暴露
            log.error("关单后自动退款失败，需人工介入：payNo={}, orderNo={}, {}",
                    payment.getPayNo(), order.getOrderNo(), e.getMessage());
        }
    }

    private Map<String, Object> replayData(MallPayment payment, boolean replay) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payNo", payment.getPayNo());
        data.put("orderNo", payment.getOrderNo());
        data.put("channel", payment.getChannel());
        data.put("status", payment.getStatus());
        data.put("statusText", MallPayment.statusText(payment.getStatus()));
        data.put("amount", payment.getAmount());
        data.put("channelTradeNo", payment.getChannelTradeNo());
        data.put("notifyTime", payment.getNotifyTime());
        data.put("replay", replay);
        return data;
    }

    /** 支付方式（写订单 pay_type）：1微信 2支付宝 3沙箱 */
    private int payTypeOf(String channel) {
        return switch (channel == null ? "" : channel) {
            case "wxpay" -> 1;
            case "alipay" -> 2;
            default -> 3;
        };
    }

    // ===== 3. 沙箱直付（保留 /shop/pay/mock 兼容既有前端与脚本） =====

    /**
     * 沙箱直付：内部走「下单 + 自签名回调」两条真实链路，保证与渠道支付同一条代码路径。
     * 对外契约不变（orderNo 进、支付状态出），旧前端与冒烟脚本无需改动。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sandboxPayNow(Long memberId, String orderNo) {
        MallOrder order = requireOwnOrder(memberId, orderNo);
        if (Integer.valueOf(1).equals(order.getPayStatus())) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderNo", order.getOrderNo());
            data.put("status", order.getStatus());
            data.put("payTime", order.getPayTime());
            data.put("replay", true);
            return data;
        }
        Map<String, Object> prepay = prepay(memberId, orderNo, SandboxPayChannel.CODE, "MOCK");
        String payNo = String.valueOf(prepay.get("payNo"));
        long amount = ((Number) prepay.get("amount")).longValue();
        String tradeNo = "SANDBOX" + LocalDateTime.now().format(TS)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        SandboxPayChannel sandbox = (SandboxPayChannel) channelRegistry.require(SandboxPayChannel.CODE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payNo", payNo);
        body.put("channelTradeNo", tradeNo);
        body.put("amount", amount);
        body.put("status", 1);
        body.put("sign", sandbox.sign(payNo, tradeNo, amount, 1));
        String raw;
        try {
            raw = JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException(500, "沙箱回调报文构造失败");
        }
        Map<String, Object> result = notify(SandboxPayChannel.CODE, raw);
        MallOrder paid = mallOrderService.getById(order.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", paid.getOrderNo());
        data.put("status", paid.getStatus());
        data.put("payTime", paid.getPayTime());
        data.put("payNo", result.get("payNo"));
        data.put("replay", result.get("replay"));
        return data;
    }

    private MallOrder requireOwnOrder(Long memberId, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(400, "参数错误：orderNo 必填");
        }
        MallOrder order = mallOrderService.getOne(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getOrderNo, orderNo).last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权支付他人订单");
        }
        return order;
    }

    // ===== 4. 后台查询与对账 =====

    public Page<Map<String, Object>> page(PaymentPageDTO dto) {
        LambdaQueryWrapper<MallPayment> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String kw = dto.getKeyword().trim();
            wrapper.and(w -> w.like(MallPayment::getPayNo, kw).or().like(MallPayment::getOrderNo, kw));
        }
        if (dto.getChannel() != null && !dto.getChannel().isBlank()) {
            wrapper.eq(MallPayment::getChannel, dto.getChannel());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallPayment::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallPayment::getId);
        Page<MallPayment> page = paymentMapper.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
        Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(p.getId()));
            row.put("payNo", p.getPayNo());
            row.put("orderNo", p.getOrderNo());
            row.put("channel", p.getChannel());
            row.put("tradeType", p.getTradeType());
            row.put("amount", p.getAmount());
            row.put("status", p.getStatus());
            row.put("statusText", MallPayment.statusText(p.getStatus()));
            row.put("channelTradeNo", p.getChannelTradeNo());
            row.put("notifyTime", p.getNotifyTime());
            row.put("createTime", p.getCreateTime());
            return row;
        }).toList());
        return result;
    }

    /** 渠道可用性（前端展示可选支付方式） */
    public Map<String, Boolean> channelAvailability() {
        return channelRegistry.availability();
    }

    /**
     * 执行对账：本地支付单 vs 渠道账单，只落差异；同日同渠道未处理差异先清空再重建（可重复执行）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runReconcile(ReconcileRunDTO dto) {
        LocalDate billDate = dto.getBillDate() == null ? LocalDate.now() : dto.getBillDate();
        String channel = dto.getChannel() == null || dto.getChannel().isBlank() ? "sandbox" : dto.getChannel();

        List<MallPayment> locals = paymentMapper.selectList(new LambdaQueryWrapper<MallPayment>()
                .eq(MallPayment::getChannel, channel)
                .in(MallPayment::getStatus, MallPayment.ST_SUCCESS, MallPayment.ST_REFUNDED,
                        MallPayment.ST_PART_REFUNDED)
                .isNotNull(MallPayment::getChannelTradeNo));
        List<MallPayment> sameDay = locals.stream()
                .filter(p -> p.getNotifyTime() != null && p.getNotifyTime().toLocalDate().equals(billDate))
                .toList();

        // 账单：未传时按沙箱镜像（本地即账单），差异恒 0
        List<ReconcileRunDTO.Bill> bills = dto.getBills() == null || dto.getBills().isEmpty()
                ? sameDay.stream().map(p -> {
            ReconcileRunDTO.Bill b = new ReconcileRunDTO.Bill();
            b.setChannelTradeNo(p.getChannelTradeNo());
            b.setAmount(p.getAmount());
            return b;
        }).toList()
                : dto.getBills();

        reconcileMapper.delete(new LambdaQueryWrapper<MallPayReconcile>()
                .eq(MallPayReconcile::getBillDate, billDate)
                .eq(MallPayReconcile::getChannel, channel)
                .eq(MallPayReconcile::getHandleStatus, MallPayReconcile.HANDLE_WAIT));

        Map<String, MallPayment> localByTradeNo = new HashMap<>();
        sameDay.forEach(p -> localByTradeNo.put(p.getChannelTradeNo(), p));
        Set<String> billTradeNos = new LinkedHashSet<>();
        List<MallPayReconcile> diffs = new ArrayList<>();

        for (ReconcileRunDTO.Bill bill : bills) {
            if (bill.getChannelTradeNo() == null) {
                continue;
            }
            billTradeNos.add(bill.getChannelTradeNo());
            MallPayment local = localByTradeNo.get(bill.getChannelTradeNo());
            long channelAmount = bill.getAmount() == null ? 0L : bill.getAmount();
            if (local == null) {
                diffs.add(diffRow(billDate, channel, null, bill.getChannelTradeNo(), channelAmount, null,
                        MallPayReconcile.DIFF_CHANNEL_ONLY));
                continue;
            }
            long localAmount = local.getAmount() == null ? 0L : local.getAmount();
            if (localAmount > channelAmount) {
                diffs.add(diffRow(billDate, channel, local.getPayNo(), bill.getChannelTradeNo(),
                        channelAmount, localAmount, MallPayReconcile.DIFF_LONG));
            } else if (localAmount < channelAmount) {
                diffs.add(diffRow(billDate, channel, local.getPayNo(), bill.getChannelTradeNo(),
                        channelAmount, localAmount, MallPayReconcile.DIFF_SHORT));
            }
        }
        for (MallPayment local : sameDay) {
            if (!billTradeNos.contains(local.getChannelTradeNo())) {
                diffs.add(diffRow(billDate, channel, local.getPayNo(), local.getChannelTradeNo(),
                        null, local.getAmount(), MallPayReconcile.DIFF_LOCAL_ONLY));
            }
        }
        diffs.forEach(reconcileMapper::insert);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("billDate", billDate);
        data.put("channel", channel);
        data.put("billCount", bills.size());
        data.put("localCount", sameDay.size());
        data.put("diffCount", diffs.size());
        data.put("diffs", diffs.stream().map(d -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("payNo", d.getPayNo());
            row.put("channelTradeNo", d.getChannelTradeNo());
            row.put("channelAmount", d.getChannelAmount());
            row.put("localAmount", d.getLocalAmount());
            row.put("diffType", d.getDiffType());
            row.put("diffText", MallPayReconcile.diffText(d.getDiffType()));
            return row;
        }).toList());
        return data;
    }

    private MallPayReconcile diffRow(LocalDate billDate, String channel, String payNo, String tradeNo,
                                     Long channelAmount, Long localAmount, int diffType) {
        MallPayReconcile row = new MallPayReconcile();
        row.setBillDate(billDate);
        row.setChannel(channel);
        row.setPayNo(payNo);
        row.setChannelTradeNo(tradeNo);
        row.setChannelAmount(channelAmount);
        row.setLocalAmount(localAmount);
        row.setDiffType(diffType);
        row.setHandleStatus(MallPayReconcile.HANDLE_WAIT);
        return row;
    }

    public Page<MallPayReconcile> reconcilePage(LocalDate billDate, String channel, Integer handleStatus,
                                                long pageNum, long pageSize) {
        LambdaQueryWrapper<MallPayReconcile> wrapper = new LambdaQueryWrapper<>();
        if (billDate != null) {
            wrapper.eq(MallPayReconcile::getBillDate, billDate);
        }
        if (channel != null && !channel.isBlank()) {
            wrapper.eq(MallPayReconcile::getChannel, channel);
        }
        if (handleStatus != null) {
            wrapper.eq(MallPayReconcile::getHandleStatus, handleStatus);
        }
        wrapper.orderByDesc(MallPayReconcile::getId);
        return reconcileMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 标记差异已处理 */
    @Transactional(rollbackFor = Exception.class)
    public void handleReconcile(Long id, Integer handleStatus, String remark, String operator) {
        MallPayReconcile row = reconcileMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(400, "对账记录不存在");
        }
        if (!Integer.valueOf(MallPayReconcile.HANDLE_WAIT).equals(row.getHandleStatus())) {
            throw new BusinessException(400, "该差异已处理，请勿重复操作");
        }
        row.setHandleStatus(handleStatus == null ? MallPayReconcile.HANDLE_CONFIRMED : handleStatus);
        row.setHandleRemark(remark);
        row.setHandleBy(operator);
        row.setHandleTime(LocalDateTime.now());
        reconcileMapper.updateById(row);
    }
}
