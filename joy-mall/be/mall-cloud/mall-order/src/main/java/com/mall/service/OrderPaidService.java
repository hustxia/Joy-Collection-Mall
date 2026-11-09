package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MarketingInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.NoticePushDTO;
import com.mall.common.feign.dto.PointGrantDTO;
import com.mall.entity.MallOrder;
import com.mall.entity.MallOrderItem;
import com.mall.entity.MallOrderLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单「支付成功」推进（V12 抽取）：mock 支付与渠道回调共用同一段推进逻辑。
 *
 * <p>为什么单独抽出来：真实支付接入后有两处入口会推进订单（沙箱直付、渠道回调），
 * 若各写一份，很容易出现「一边累加销量、一边忘了核销券」的偏差。这里统一：
 * 条件 UPDATE 推进状态 → 销量 → 券核销 → 日志 → 积分 → 站内消息，
 * 其中积分与消息失败只降级告警，不回滚已成功的支付。
 */
@Slf4j
@Service
public class OrderPaidService {

    /** 订单状态：待发货（支付成功后的状态） */
    private static final int STATUS_PAID = 20;
    /** 订单状态：待付款 */
    private static final int STATUS_UNPAID = 10;

    private final MallOrderService mallOrderService;
    private final MallOrderItemService mallOrderItemService;
    private final MallOrderLogService mallOrderLogService;
    private final GoodsInnerClient goodsInnerClient;
    private final MarketingInnerClient marketingInnerClient;
    private final MemberInnerClient memberInnerClient;
    private final JdbcTemplate jdbcTemplate;

    public OrderPaidService(MallOrderService mallOrderService, MallOrderItemService mallOrderItemService,
                            MallOrderLogService mallOrderLogService, GoodsInnerClient goodsInnerClient,
                            MarketingInnerClient marketingInnerClient, MemberInnerClient memberInnerClient,
                            JdbcTemplate jdbcTemplate) {
        this.mallOrderService = mallOrderService;
        this.mallOrderItemService = mallOrderItemService;
        this.mallOrderLogService = mallOrderLogService;
        this.goodsInnerClient = goodsInnerClient;
        this.marketingInnerClient = marketingInnerClient;
        this.memberInnerClient = memberInnerClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 推进为已支付（幂等）。
     *
     * @param order  订单（调用方已校验归属）
     * @param payNo  支付单号（写入 mall_order.pay_order_no）
     * @param payType 支付方式：1微信 2支付宝 3沙箱
     * @return true=本次推进成功；false=已支付或状态不允许（调用方按重放处理）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean advance(MallOrder order, String payNo, int payType) {
        if (Integer.valueOf(1).equals(order.getPayStatus())) {
            return false;
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_order SET pay_status = 1, status = ?, pay_type = ?, pay_time = NOW(), pay_order_no = ?"
                        + " WHERE id = ? AND status = ? AND pay_status = 0 AND deleted = 0",
                STATUS_PAID, payType, payNo, order.getId(), STATUS_UNPAID);
        if (affected == 0) {
            return false;
        }
        // 销量累加（支付成功口径）：按 SKU/商品聚合
        List<MallOrderItem> items = mallOrderItemService.list(
                new LambdaQueryWrapper<MallOrderItem>().eq(MallOrderItem::getOrderId, order.getId()));
        Map<Long, Integer> skuSales = new LinkedHashMap<>();
        Map<Long, Integer> goodsSales = new LinkedHashMap<>();
        for (MallOrderItem item : items) {
            if (item.getSkuId() != null) {
                skuSales.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
            }
            if (item.getGoodsId() != null) {
                goodsSales.merge(item.getGoodsId(), item.getQuantity(), Integer::sum);
            }
        }
        try {
            if (!skuSales.isEmpty()) {
                goodsInnerClient.incrementSkuSales(skuSales);
            }
            if (!goodsSales.isEmpty()) {
                goodsInnerClient.incrementGoodsSales(goodsSales);
            }
        } catch (Exception e) {
            log.warn("支付后累加销量失败（已降级），订单 {}：{}", order.getOrderNo(), e.getMessage());
        }
        try {
            marketingInnerClient.useByOrder(order.getId());
        } catch (Exception e) {
            log.warn("支付后核销优惠券失败（已降级），订单 {}：{}", order.getOrderNo(), e.getMessage());
        }
        logPaid(order, payNo);
        MallOrder paid = mallOrderService.getById(order.getId());
        grantPayPoints(paid);
        pushPayNotice(paid);
        return true;
    }

    private void logPaid(MallOrder order, String payNo) {
        MallOrderLog logRow = new MallOrderLog();
        logRow.setOrderId(order.getId());
        logRow.setOrderNo(order.getOrderNo());
        logRow.setContent("支付成功，支付单号 " + payNo);
        Long memberId = UserContext.getUserId();
        String operator = String.valueOf(UserContext.getUsername());
        if (memberId != null) {
            try {
                MemberBrief member = memberInnerClient.member(memberId);
                if (member != null && member.getNickname() != null) {
                    operator = member.getNickname();
                }
            } catch (Exception ignored) {
                // 昵称取不到不影响日志落库
            }
        }
        logRow.setOperator(operator);
        logRow.setOperatorType(1);
        mallOrderLogService.save(logRow);
    }

    /** 消费返积分：1 元 = 1 积分（不足 1 元不返） */
    private void grantPayPoints(MallOrder paid) {
        try {
            long fen = paid.getPayAmount() == null ? 0L : paid.getPayAmount();
            int reward = (int) (fen / 100);
            if (reward <= 0) {
                return;
            }
            PointGrantDTO grant = new PointGrantDTO();
            grant.setMemberId(paid.getMemberId());
            grant.setChangeType(1);
            grant.setPoints(reward);
            grant.setOrderId(paid.getId());
            grant.setRemark("消费返积分，订单号 " + paid.getOrderNo());
            memberInnerClient.grantPoints(grant);
        } catch (Exception e) {
            log.warn("消费返积分失败（已降级），订单 {}：{}", paid.getOrderNo(), e.getMessage());
        }
    }

    /** 站内消息：订单支付成功 */
    private void pushPayNotice(MallOrder paid) {
        try {
            NoticePushDTO notice = new NoticePushDTO();
            notice.setMemberId(paid.getMemberId());
            notice.setType(1);
            notice.setTitle("订单支付成功");
            notice.setContent("订单 " + paid.getOrderNo() + " 支付成功，实付 ¥" + yuanText(paid.getPayAmount())
                    + "，商家将尽快发货");
            notice.setBizId(paid.getOrderNo());
            memberInnerClient.pushNotice(notice);
        } catch (Exception e) {
            log.warn("支付成功消息推送失败（已降级），订单 {}：{}", paid.getOrderNo(), e.getMessage());
        }
    }

    private static String yuanText(Long fen) {
        return java.math.BigDecimal.valueOf(fen == null ? 0L : fen)
                .divide(java.math.BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }
}
