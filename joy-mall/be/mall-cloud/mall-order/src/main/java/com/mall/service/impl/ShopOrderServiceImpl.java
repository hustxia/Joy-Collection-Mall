package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MarketingInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.RefundInnerClient;
import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.CartBrief;
import com.mall.common.feign.dto.CouponBrief;
import com.mall.common.feign.dto.CouponRecordBrief;
import com.mall.common.feign.dto.FreightQuoteRequest;
import com.mall.common.feign.dto.FreightQuoteResult;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.MerchantBrief;
import com.mall.common.feign.dto.NoticePushDTO;
import com.mall.common.feign.dto.RefundBrief;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.common.context.UserContext;
import com.mall.dto.ShopConfirmDTO;
import com.mall.dto.ShopSubmitDTO;
import com.mall.entity.MallOrder;
import com.mall.entity.MallOrderItem;
import com.mall.entity.MallOrderLog;
import com.mall.service.MallOrderItemService;
import com.mall.service.MallOrderLogService;
import com.mall.service.MallOrderService;
import com.mall.service.OrderStockBackfillService;
import com.mall.service.ShopOrderService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端订单业务实现：状态机、拆单、扣库存、优惠券、售后回填等全部业务规则在此，
 * Controller 只做参数绑定与结果包装。
 */
@Slf4j
@Service
public class ShopOrderServiceImpl implements ShopOrderService {

    private final MallOrderService mallOrderService;
    private final MallOrderItemService mallOrderItemService;
    private final MallOrderLogService mallOrderLogService;
    private final OrderStockBackfillService orderStockBackfillService;
    private final GoodsInnerClient goodsInnerClient;
    private final MemberInnerClient memberInnerClient;
    private final MarketingInnerClient marketingInnerClient;
    private final RefundInnerClient refundInnerClient;
    private final MerchantInnerClient merchantInnerClient;
    /** 只用于本域 mall_order 的条件状态迁移，不跨域 */
    private final JdbcTemplate jdbcTemplate;

    public ShopOrderServiceImpl(MallOrderService mallOrderService,
                                MallOrderItemService mallOrderItemService,
                                MallOrderLogService mallOrderLogService,
                                OrderStockBackfillService orderStockBackfillService,
                                GoodsInnerClient goodsInnerClient,
                                MemberInnerClient memberInnerClient,
                                MarketingInnerClient marketingInnerClient,
                                RefundInnerClient refundInnerClient,
                                MerchantInnerClient merchantInnerClient,
                                JdbcTemplate jdbcTemplate) {
        this.mallOrderService = mallOrderService;
        this.mallOrderItemService = mallOrderItemService;
        this.mallOrderLogService = mallOrderLogService;
        this.orderStockBackfillService = orderStockBackfillService;
        this.goodsInnerClient = goodsInnerClient;
        this.memberInnerClient = memberInnerClient;
        this.marketingInnerClient = marketingInnerClient;
        this.refundInnerClient = refundInnerClient;
        this.merchantInnerClient = merchantInnerClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> confirm(Long memberId, ShopConfirmDTO dto) {
        List<Line> lines = resolveLines(memberId, dto == null ? null : dto.getSkuId(),
                dto == null ? null : dto.getNum());
        long totalAmount = lines.stream().filter(l -> l.buyable).mapToLong(l -> l.price * l.num).sum();
        // V10：确认页要能显示「按店铺拆单」与跨店用券限制，逐行补店铺名（失败降级为 null）
        Map<Long, String> merchantNames = new LinkedHashMap<>();
        for (Line line : lines) {
            Long mid = line.merchantId == null ? PLATFORM_MERCHANT_ID : line.merchantId;
            merchantNames.computeIfAbsent(mid, this::merchantNameOf);
        }
        // V12：按店铺分别计运费（跨店拆单后各订单各承担本店运费）
        AddressBrief address = addressOf(memberId, dto == null ? null : dto.getAddressId());
        Map<Long, FreightQuoteResult> freights = quoteFreight(lines, address);
        long freightTotal = freights.values().stream().mapToLong(ShopOrderServiceImpl::freightOf).sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", lines.stream().map(l -> l.toMap(merchantNames)).toList());
        data.put("totalAmount", totalAmount);
        data.put("discountAmount", 0L);
        data.put("freightAmount", freightTotal);
        data.put("payAmount", totalAmount + freightTotal);
        data.put("usableCoupons", usableCoupons(memberId, totalAmount));
        // 前端据此提示：多店铺会拆成多单，且不能跨店用券
        data.put("merchantCount", (int) lines.stream()
                .map(l -> l.merchantId == null ? PLATFORM_MERCHANT_ID : l.merchantId)
                .distinct().count());
        data.put("merchantFreights", freights.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("merchantId", String.valueOf(e.getKey()));
            row.put("merchantName", merchantNames.get(e.getKey()));
            row.put("amount", freightOf(e.getValue()));
            row.put("freeGap", e.getValue() == null || e.getValue().getFreeGap() == null
                    ? 0L : e.getValue().getFreeGap());
            row.put("tip", e.getValue() == null ? null : e.getValue().getTip());
            return row;
        }).toList());
        data.put("freightTip", firstTip(freights));
        data.put("freeShippingGap", freights.values().stream()
                .filter(r -> r != null && r.getFreeGap() != null)
                .mapToLong(FreightQuoteResult::getFreeGap).sum());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long memberId, ShopSubmitDTO dto) {
        if (dto == null || dto.getAddressId() == null) {
            throw new BusinessException(400, "请选择收货地址");
        }
        AddressBrief address = memberInnerClient.address(dto.getAddressId());
        if (address == null) {
            throw new BusinessException(400, "收货地址不存在");
        }
        if (!memberId.equals(address.getMemberId())) {
            throw new BusinessException(403, "无权使用他人地址");
        }
        List<Line> lines = resolveLines(memberId, dto.getSkuId(), dto.getNum());
        List<Line> buyable = lines.stream().filter(l -> l.buyable).toList();
        if (buyable.isEmpty()) {
            throw new BusinessException(400, "没有可下单的商品");
        }
        if (buyable.size() != lines.size()) {
            Line firstBad = lines.stream().filter(l -> !l.buyable).findFirst().orElseThrow();
            throw new BusinessException(400, "「" + firstBad.goodsName + "」" + firstBad.reason);
        }

        // V10：购物车按商家拆单——一个订单只属于一个商家，各自独立支付与履约
        Map<Long, List<Line>> byMerchant = groupByMerchant(buyable);
        if (byMerchant.size() > 1 && dto.getCouponRecordId() != null) {
            // 优惠券是订单级优惠，拆单后无法公平分摊：明确拒绝，避免一笔券核销到多笔订单
            throw new BusinessException(400, "所选商品来自 " + byMerchant.size()
                    + " 个店铺，优惠券不支持跨店铺使用，请分店铺下单");
        }
        long totalAmount = buyable.stream().mapToLong(l -> l.price * l.num).sum();

        // V12：运费按店铺分别计算（券只抵商品金额，不抵运费）
        Map<Long, FreightQuoteResult> freights = quoteFreight(buyable, address);

        // 券校验（满减券 discount=面额），提交即锁定（order_id 占位），支付成功置已用
        CouponHolder couponRecord = null;
        long discountAmount = 0L;
        if (dto.getCouponRecordId() != null) {
            couponRecord = lockableCoupon(memberId, dto.getCouponRecordId(), totalAmount);
            discountAmount = couponRecord.getCouponAmount();
        }

        // 条件扣库存（逐行调商品域，失败即回补已扣部分，保留逐行「库存不足」文案）
        Map<Long, Integer> deducted = new LinkedHashMap<>();
        for (Line line : buyable) {
            boolean ok = Boolean.TRUE.equals(goodsInnerClient.deductStock(Map.of(line.skuId, line.num)));
            if (!ok) {
                if (!deducted.isEmpty()) {
                    goodsInnerClient.restoreStock(deducted);
                }
                throw new BusinessException(400, "「" + line.goodsName + "」库存不足");
            }
            deducted.merge(line.skuId, line.num, Integer::sum);
        }

        List<Map<String, Object>> orderRows = new ArrayList<>();
        long payTotal = 0L;
        Long firstOrderId = null;
        for (Map.Entry<Long, List<Line>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey();
            List<Line> shopLines = entry.getValue();
            long shopTotal = shopLines.stream().mapToLong(l -> l.price * l.num).sum();
            // 单店铺才可能有券（跨店已在上面拦截），券全额落在唯一订单上
            long shopDiscount = Math.min(discountAmount, shopTotal);
            long shopFreight = freightOf(freights.get(merchantId));
            // 运费不参与优惠：pay_amount = 商品额 − 优惠 + 运费，恒等式见 REQ-P0-02 验收第 6 条
            long shopPay = Math.max(shopTotal - shopDiscount, 0L) + shopFreight;

            MallOrder order = new MallOrder();
            order.setOrderNo(ShopOrderService.newOrderNo());
            order.setMemberId(memberId);
            order.setMerchantId(merchantId);
            order.setTotalAmount(shopTotal);
            order.setFreightAmount(shopFreight);
            order.setDiscountAmount(shopDiscount);
            order.setPayAmount(shopPay);
            order.setStatus(STATUS_UNPAID);
            order.setPayStatus(0);
            order.setReceiverName(address.getReceiverName());
            order.setReceiverPhone(address.getReceiverPhone());
            order.setReceiverAddress(joinAddress(address));
            order.setBuyerRemark(dto.getBuyerRemark());
            mallOrderService.save(order);

            for (Line line : shopLines) {
                MallOrderItem item = new MallOrderItem();
                item.setOrderId(order.getId());
                item.setMerchantId(merchantId);
                item.setGoodsId(line.goodsId);
                item.setSkuId(line.skuId);
                item.setGoodsName(line.goodsName);
                item.setSkuImage(line.skuImage);
                item.setSpecJson(line.specJson);
                item.setPrice(line.price);
                item.setQuantity(line.num);
                item.setTotalAmount(line.price * line.num);
                mallOrderItemService.save(item);
            }
            writeLog(order.getId(), order.getOrderNo(),
                    "提交订单，商品 " + shopTotal + " 分，运费 " + shopFreight + " 分，实付 " + shopPay + " 分", 1);
            if (firstOrderId == null) {
                firstOrderId = order.getId();
            }
            payTotal += shopPay;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", String.valueOf(order.getId()));
            row.put("orderNo", order.getOrderNo());
            row.put("merchantId", String.valueOf(merchantId));
            row.put("merchantName", merchantNameOf(merchantId));
            row.put("payAmount", shopPay);
            row.put("freightAmount", shopFreight);
            orderRows.add(row);
        }

        if (couponRecord != null) {
            boolean locked = Boolean.TRUE.equals(
                    marketingInnerClient.lockRecord(couponRecord.getId(), firstOrderId));
            if (!locked) {
                throw new BusinessException(400, "优惠券已被占用，请刷新后重试");
            }
        }

        // 清购物车已下单行（物理删除：逻辑删会撞 uk_member_sku(member_id,sku_id,deleted)）
        if (dto.getSkuId() == null) {
            List<Long> cartIds = buyable.stream().map(l -> l.cartId).filter(Objects::nonNull).toList();
            if (!cartIds.isEmpty()) {
                memberInnerClient.clearCart(memberId, cartIds);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        // 兼容旧前端：orderId/orderNo/payAmount 取第一笔；orders 为完整拆单结果
        data.put("orderId", String.valueOf(firstOrderId));
        data.put("orderNo", orderRows.get(0).get("orderNo"));
        data.put("payAmount", payTotal);
        data.put("orderCount", orderRows.size());
        data.put("orderNos", orderRows.stream().map(r -> r.get("orderNo")).toList());
        data.put("orders", orderRows);
        return data;
    }

    @Override
    public Page<Map<String, Object>> page(Long memberId, Integer status, long pageNum, long pageSize) {
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallOrder::getMemberId, memberId);
        if (status != null) {
            wrapper.eq(MallOrder::getStatus, status);
        }
        wrapper.orderByDesc(MallOrder::getId);
        Page<MallOrder> page = mallOrderService.page(new Page<>(pageNum, Math.min(pageSize, 50)), wrapper);
        Map<Long, List<MallOrderItem>> itemMap = itemsOf(page.getRecords());
        Map<Long, RefundBrief> refundMap = latestRefundsOf(page.getRecords());
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(o -> {
                    Map<String, Object> m = orderSummary(o, itemMap.getOrDefault(o.getId(), List.of()));
                    // V4 售后闭环：列表行带售后角标（refundStatus 1/2=售后中 4=已退款）
                    RefundBrief r = refundMap.get(o.getId());
                    if (r != null) {
                        m.put("refundStatus", r.getStatus());
                        m.put("refundNo", r.getRefundNo());
                        m.put("refundId", String.valueOf(r.getId()));
                    }
                    return m;
                }).toList());
        return voPage;
    }

    @Override
    public Map<String, Object> detail(Long memberId, Long id) {
        MallOrder order = ownOrder(memberId, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", order);
        data.put("merchantId", String.valueOf(order.getMerchantId() == null ? PLATFORM_MERCHANT_ID : order.getMerchantId()));
        data.put("merchantName", merchantNameOf(order.getMerchantId()));
        LambdaQueryWrapper<MallOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(MallOrderItem::getOrderId, id);
        data.put("items", mallOrderItemService.list(itemWrapper));
        LambdaQueryWrapper<MallOrderLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.eq(MallOrderLog::getOrderId, id).orderByAsc(MallOrderLog::getId);
        data.put("logs", mallOrderLogService.list(logWrapper).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", l.getContent());
            m.put("operator", l.getOperator());
            m.put("operatorType", l.getOperatorType());
            m.put("createTime", l.getCreateTime());
            return m;
        }).toList());
        // V4 售后闭环：详情带最新售后单（无则 null），前端决定「申请售后 / 查看售后」
        data.put("refund", latestRefundOf(id));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long memberId, Long orderId) {
        MallOrder order = ownOrder(memberId, orderId);
        int affected = jdbcTemplate.update(
                "UPDATE mall_order SET status = ? WHERE id = ? AND status = ? AND deleted = 0",
                STATUS_CANCELLED, order.getId(), STATUS_UNPAID);
        if (affected == 0) {
            throw new BusinessException(400, "仅待付款订单可取消，请刷新后重试");
        }
        // 回补 SKU 库存：复用共享方法（与超时关单同一口径，防两处维护 + 防并发双重回补）
        orderStockBackfillService.backfill(List.of(order.getId()));
        marketingInnerClient.releaseByOrder(order.getId());
        writeLog(order.getId(), order.getOrderNo(), "取消订单，库存已回补", 1);
    }

    @Override
    public void confirmReceipt(Long memberId, Long orderId) {
        MallOrder order = ownOrder(memberId, orderId);
        int affected = jdbcTemplate.update(
                "UPDATE mall_order SET status = ?, finish_time = NOW() WHERE id = ? AND status = ? AND deleted = 0",
                STATUS_FINISHED, order.getId(), STATUS_DELIVERED);
        if (affected == 0) {
            throw new BusinessException(400, "仅已发货订单可确认收货，请刷新后重试");
        }
        writeLog(order.getId(), order.getOrderNo(), "确认收货，订单完成", 1);
        // V10：订单完成 → 商家货款结算入账（幂等；失败只告警，不回滚确认收货）
        settleMerchant(order);
        // V7 站内消息：订单完成，引导评价晒单（失败降级，不影响确认收货主流程）
        try {
            NoticePushDTO notice = new NoticePushDTO();
            notice.setMemberId(order.getMemberId());
            notice.setType(1);
            notice.setTitle("订单已完成");
            notice.setContent("订单 " + order.getOrderNo() + " 已确认收货，欢迎前往评价晒单");
            notice.setBizId(order.getOrderNo());
            memberInnerClient.pushNotice(notice);
        } catch (Exception e) {
            log.warn("订单完成消息推送失败（已降级），订单 {}：{}", order.getOrderNo(), e.getMessage());
        }
    }

    // ===== 私有辅助 =====

    /** 按商家分组，保持购物车原始顺序（LinkedHashMap） */
    private Map<Long, List<Line>> groupByMerchant(List<Line> lines) {
        Map<Long, List<Line>> map = new LinkedHashMap<>();
        for (Line line : lines) {
            Long merchantId = line.merchantId == null ? PLATFORM_MERCHANT_ID : line.merchantId;
            map.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(line);
        }
        return map;
    }

    // ===== V12 运费（商品域试算，订单域只做「按店铺分组 + 求和」） =====

    /** 确认页允许未选地址（返回 null 时按模板默认规则计费）；传了地址就必须是本人的 */
    private AddressBrief addressOf(Long memberId, Long addressId) {
        if (addressId == null) {
            return null;
        }
        AddressBrief address = memberInnerClient.address(addressId);
        if (address == null) {
            throw new BusinessException(400, "收货地址不存在");
        }
        if (!memberId.equals(address.getMemberId())) {
            throw new BusinessException(403, "无权使用他人地址");
        }
        return address;
    }

    /**
     * 按店铺分组试算运费。
     * <p>商品域不可用或返回 null 时降级为 0 并告警：预览页宁可少显示运费，
     * 也不能因为运费服务抖动让用户打不开确认页（下单时会再次计算并落库）。
     */
    private Map<Long, FreightQuoteResult> quoteFreight(List<Line> lines, AddressBrief address) {
        Map<Long, List<Line>> groups = groupByMerchant(lines.stream().filter(l -> l.buyable).toList());
        Map<Long, FreightQuoteResult> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Line>> entry : groups.entrySet()) {
            FreightQuoteRequest request = new FreightQuoteRequest();
            request.setMerchantId(entry.getKey());
            request.setProvince(address == null ? null : address.getProvince());
            request.setCity(address == null ? null : address.getCity());
            request.setGoodsAmount(entry.getValue().stream().mapToLong(l -> l.price * l.num).sum());
            List<FreightQuoteRequest.Item> items = new ArrayList<>();
            for (Line line : entry.getValue()) {
                FreightQuoteRequest.Item item = new FreightQuoteRequest.Item();
                item.setSkuId(line.skuId);
                item.setGoodsId(line.goodsId);
                item.setQuantity(line.num);
                items.add(item);
            }
            request.setItems(items);
            try {
                result.put(entry.getKey(), goodsInnerClient.freightQuote(request));
            } catch (Exception e) {
                log.warn("运费试算失败（降级为 0），merchantId={}：{}", entry.getKey(), e.getMessage());
                result.put(entry.getKey(), null);
            }
        }
        return result;
    }

    private static long freightOf(FreightQuoteResult result) {
        return result == null || result.getAmount() == null ? 0L : Math.max(result.getAmount(), 0L);
    }

    private static String firstTip(Map<Long, FreightQuoteResult> freights) {
        return freights.values().stream()
                .filter(r -> r != null && r.getTip() != null && !r.getTip().isBlank())
                .map(FreightQuoteResult::getTip)
                .findFirst().orElse("");
    }

    /** 解析下单行：立即购买（skuId+num）或购物车勾选项；逐行核算 buyable/reason */
    private List<Line> resolveLines(Long memberId, Long skuId, Integer num) {
        List<Line> lines = new ArrayList<>();
        if (skuId != null) {
            Line line = new Line();
            line.skuId = skuId;
            line.num = (num == null || num < 1) ? 1 : num;
            lines.add(line);
        } else {
            List<CartBrief> carts = memberInnerClient.cartList(memberId, 1);
            if (carts == null || carts.isEmpty()) {
                throw new BusinessException(400, "请先勾选要下单的商品");
            }
            for (CartBrief cart : carts) {
                Line line = new Line();
                line.cartId = cart.getId();
                line.skuId = cart.getSkuId();
                line.num = cart.getNum();
                lines.add(line);
            }
        }
        List<Long> skuIds = lines.stream().map(l -> l.skuId).toList();
        Map<Long, SkuBrief> skuMap = goodsInnerClient.skuByIds(skuIds).stream()
                .collect(Collectors.toMap(SkuBrief::getId, Function.identity()));
        List<Long> goodsIds = skuMap.values().stream().map(SkuBrief::getGoodsId).distinct().toList();
        Map<Long, GoodsBrief> goodsMap = goodsIds.isEmpty() ? Map.of()
                : goodsInnerClient.goodsByIds(goodsIds).stream()
                        .collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));
        for (Line line : lines) {
            SkuBrief sku = skuMap.get(line.skuId);
            GoodsBrief goods = sku == null ? null : goodsMap.get(sku.getGoodsId());
            if (sku == null || goods == null) {
                line.buyable = false;
                line.reason = "商品不存在";
                continue;
            }
            line.goodsId = goods.getId();
            line.merchantId = goods.getMerchantId() == null ? PLATFORM_MERCHANT_ID : goods.getMerchantId();
            line.goodsName = goods.getName();
            line.skuImage = sku.getImage() != null ? sku.getImage() : goods.getMainImage();
            line.specJson = sku.getSpecJson();
            line.price = sku.getPrice() == null ? 0L : sku.getPrice();
            line.stock = sku.getStock() == null ? 0 : sku.getStock();
            if (!Integer.valueOf(1).equals(goods.getStatus()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                line.buyable = false;
                line.reason = "商品已下架";
            } else if (line.stock < line.num) {
                line.buyable = false;
                line.reason = "库存不足，仅剩 " + line.stock + " 件";
            }
        }
        return lines;
    }

    /** 可用券：本人未用未锁定 + 券上架且在有效期 + 订单总额达门槛（营销域内部接口已过滤可用记录） */
    private List<Map<String, Object>> usableCoupons(Long memberId, long totalAmount) {
        List<CouponRecordBrief> records = marketingInnerClient.availableRecords(memberId);
        List<Map<String, Object>> result = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return result;
        }
        List<Long> couponIds = records.stream().map(CouponRecordBrief::getCouponId).toList();
        Map<Long, CouponBrief> couponMap = marketingInnerClient.couponByIds(couponIds).stream()
                .collect(Collectors.toMap(CouponBrief::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (CouponRecordBrief record : records) {
            CouponBrief coupon = couponMap.get(record.getCouponId());
            if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())) {
                continue;
            }
            if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
                continue;
            }
            if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
                continue;
            }
            if (!Integer.valueOf(1).equals(coupon.getType())) {
                continue;
            }
            long threshold = coupon.getThresholdAmount() == null ? 0L : coupon.getThresholdAmount();
            if (totalAmount < threshold) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", String.valueOf(record.getId()));
            m.put("couponId", String.valueOf(coupon.getId()));
            m.put("name", coupon.getName());
            m.put("amount", coupon.getAmount());
            m.put("thresholdAmount", threshold);
            m.put("endTime", coupon.getEndTime());
            result.add(m);
        }
        return result;
    }

    /** 提交时锁定券：复用可用券口径，命中即返回（含面额），不命中 400 */
    private CouponHolder lockableCoupon(Long memberId, Long recordId, long totalAmount) {
        List<Map<String, Object>> usable = usableCoupons(memberId, totalAmount);
        for (Map<String, Object> m : usable) {
            if (String.valueOf(recordId).equals(m.get("recordId"))) {
                CouponHolder holder = new CouponHolder();
                holder.setId(recordId);
                holder.setCouponAmount((Long) m.get("amount"));
                return holder;
            }
        }
        throw new BusinessException(400, "优惠券不可用（已使用/未达门槛/已过期）");
    }

    private MallOrder ownOrder(Long memberId, Long id) {
        MallOrder order = mallOrderService.getById(id);
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(403, "无权操作他人订单");
        }
        return order;
    }

    private Map<Long, List<MallOrderItem>> itemsOf(List<MallOrder> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(MallOrder::getId).toList();
        LambdaQueryWrapper<MallOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MallOrderItem::getOrderId, orderIds).orderByAsc(MallOrderItem::getId);
        return mallOrderItemService.list(wrapper).stream()
                .collect(Collectors.groupingBy(MallOrderItem::getOrderId));
    }

    // ===== V4 售后闭环：订单接口的售后信息回填（经售后域内部接口） =====

    /** 批量取每个订单的最新一条售后单；无单的订单不在 map 里 */
    private Map<Long, RefundBrief> latestRefundsOf(List<MallOrder> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(MallOrder::getId).toList();
        Map<Long, RefundBrief> result = new LinkedHashMap<>();
        for (RefundBrief r : refundInnerClient.latestByOrderIds(orderIds)) {
            result.put(r.getOrderId(), r);
        }
        return result;
    }

    /** 单订单最新售后单，无则 null */
    private RefundBrief latestRefundOf(Long orderId) {
        return refundInnerClient.latestByOrderId(orderId);
    }

    private Map<String, Object> orderSummary(MallOrder order, List<MallOrderItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(order.getId()));
        m.put("orderNo", order.getOrderNo());
        m.put("status", order.getStatus());
        m.put("merchantId", String.valueOf(order.getMerchantId() == null ? PLATFORM_MERCHANT_ID : order.getMerchantId()));
        m.put("merchantName", merchantNameOf(order.getMerchantId()));
        m.put("payAmount", order.getPayAmount());
        m.put("totalAmount", order.getTotalAmount());
        m.put("freightAmount", order.getFreightAmount());
        m.put("discountAmount", order.getDiscountAmount());
        m.put("createTime", order.getCreateTime());
        m.put("items", items.stream().map(i -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("skuImage", i.getSkuImage());
            im.put("goodsName", i.getGoodsName());
            im.put("price", i.getPrice());
            im.put("quantity", i.getQuantity());
            return im;
        }).toList());
        return m;
    }

    /** 订单流转日志：operator_type 1 会员 / 2 管理员 / 3 系统 */
    private void writeLog(Long orderId, String orderNo, String content, int operatorType) {
        MallOrderLog log = new MallOrderLog();
        log.setOrderId(orderId);
        log.setOrderNo(orderNo);
        log.setContent(content);
        log.setOperator(operatorName());
        log.setOperatorType(operatorType);
        mallOrderLogService.save(log);
    }

    /** V10 订单完成结算：向商家域入账，重复事件由商家域幂等；异常只降级告警 */
    private void settleMerchant(MallOrder order) {
        try {
            Long merchantId = order.getMerchantId() == null ? PLATFORM_MERCHANT_ID : order.getMerchantId();
            Boolean done = merchantInnerClient.settle(order.getId(), merchantId, order.getOrderNo(),
                    order.getPayAmount());
            if (Boolean.TRUE.equals(done)) {
                writeLog(order.getId(), order.getOrderNo(), "商家货款已结算（商家ID " + merchantId + "）", 3);
            }
        } catch (Exception e) {
            log.warn("商家货款结算失败（已降级，待补结算），订单 {}：{}", order.getOrderNo(), e.getMessage());
        }
    }

    /** 店铺名（展示用）：商家域不可用时回落 null，不影响订单主信息 */
    private String merchantNameOf(Long merchantId) {
        try {
            Long id = merchantId == null ? PLATFORM_MERCHANT_ID : merchantId;
            MerchantBrief brief = merchantInnerClient.merchant(id);
            return brief == null ? null : brief.getName();
        } catch (Exception e) {
            log.warn("查询店铺名失败（已降级），merchantId={}：{}", merchantId, e.getMessage());
            return null;
        }
    }

    private String operatorName() {
        Long memberId = UserContext.getUserId();
        MemberBrief member = memberId == null ? null : memberInnerClient.member(memberId);
        return member != null && member.getNickname() != null ? member.getNickname()
                : String.valueOf(UserContext.getUsername());
    }

    private String joinAddress(AddressBrief a) {
        StringBuilder sb = new StringBuilder();
        if (a.getProvince() != null) {
            sb.append(a.getProvince());
        }
        if (a.getCity() != null) {
            sb.append(a.getCity());
        }
        if (a.getDistrict() != null) {
            sb.append(a.getDistrict());
        }
        if (a.getDetail() != null) {
            sb.append(a.getDetail());
        }
        return sb.toString();
    }

    /** 下单行内部模型 */
    private static class Line {
        private Long cartId;
        private Long skuId;
        private Long goodsId;
        private Long merchantId;
        private String goodsName;
        private String skuImage;
        private String specJson;
        private long price;
        private int stock;
        private int num;
        private boolean buyable = true;
        private String reason;

        private Map<String, Object> toMap(Map<Long, String> merchantNames) {
            Long mid = merchantId == null ? PLATFORM_MERCHANT_ID : merchantId;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", String.valueOf(skuId));
            m.put("goodsId", goodsId == null ? null : String.valueOf(goodsId));
            m.put("goodsName", goodsName);
            m.put("skuImage", skuImage);
            m.put("specJson", specJson);
            m.put("merchantId", String.valueOf(mid));
            m.put("merchantName", merchantNames == null ? null : merchantNames.get(mid));
            m.put("price", price);
            m.put("num", num);
            m.put("stock", stock);
            m.put("buyable", buyable);
            m.put("reason", reason);
            return m;
        }
    }

    @Data
    private static class CouponHolder {
        private Long id;
        private Long couponAmount;
    }
}
