package com.mall.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.OrderLogAppendDTO;
import com.mall.common.feign.dto.OrderLogBrief;
import com.mall.common.feign.dto.PromoOrderCreateDTO;
import com.mall.entity.MallOrder;
import com.mall.entity.MallOrderItem;
import com.mall.entity.MallOrderLog;
import com.mall.service.MallOrderItemService;
import com.mall.service.MallOrderLogService;
import com.mall.service.MallOrderService;
import com.mall.service.ShopOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订单域内部端点（/inner/**，免鉴权）：供售后域读取订单/明细/日志、追加订单流转日志。
 *
 * <p>内部 RPC 直接返回原始载荷（不套 Result 壳）。
 */
@RestController
@RequestMapping("/inner")
public class InnerOrderController {

    private final MallOrderService mallOrderService;
    private final MallOrderItemService mallOrderItemService;
    private final MallOrderLogService mallOrderLogService;
    private final MemberInnerClient memberInnerClient;
    private final com.mall.service.InvoiceService invoiceService;
    private final com.mall.service.DeliveryService deliveryService;

    public InnerOrderController(MallOrderService mallOrderService,
                                MallOrderItemService mallOrderItemService,
                                MallOrderLogService mallOrderLogService,
                                MemberInnerClient memberInnerClient,
                                com.mall.service.InvoiceService invoiceService,
                                com.mall.service.DeliveryService deliveryService) {
        this.mallOrderService = mallOrderService;
        this.mallOrderItemService = mallOrderItemService;
        this.mallOrderLogService = mallOrderLogService;
        this.memberInnerClient = memberInnerClient;
        this.invoiceService = invoiceService;
        this.deliveryService = deliveryService;
    }

    /** V12 发票红冲：售后域退款完成后回调；无已开票发票返回 false（不算失败） */
    @PostMapping("/invoice/red-flush")
    public Boolean redFlushInvoice(@RequestParam("orderId") Long orderId,
                                   @RequestParam("remark") String remark) {
        return invoiceService.redFlushByOrder(orderId, remark);
    }

    @GetMapping("/order/{id}")
    public OrderBrief order(@PathVariable("id") Long id) {
        MallOrder o = mallOrderService.getById(id);
        if (o == null) {
            return null;
        }
        OrderBrief b = new OrderBrief();
        b.setId(o.getId());
        b.setOrderNo(o.getOrderNo());
        b.setMemberId(o.getMemberId());
        b.setMerchantId(o.getMerchantId());
        b.setStatus(o.getStatus());
        b.setPayAmount(o.getPayAmount());
        b.setPayOrderNo(o.getPayOrderNo());
        b.setReceiverName(o.getReceiverName());
        b.setReceiverPhone(o.getReceiverPhone());
        b.setReceiverAddress(o.getReceiverAddress());
        return b;
    }

    @GetMapping("/order-item")
    public List<OrderItemBrief> orderItems(@RequestParam("orderId") Long orderId) {
        LambdaQueryWrapper<MallOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallOrderItem::getOrderId, orderId).orderByAsc(MallOrderItem::getId);
        return mallOrderItemService.list(wrapper).stream().map(i -> {
            OrderItemBrief b = new OrderItemBrief();
            b.setId(i.getId());
            b.setOrderId(i.getOrderId());
            b.setGoodsId(i.getGoodsId());
            b.setSkuId(i.getSkuId());
            b.setGoodsName(i.getGoodsName());
            b.setSkuImage(i.getSkuImage());
            b.setSpecJson(i.getSpecJson());
            b.setPrice(i.getPrice());
            b.setQuantity(i.getQuantity());
            b.setTotalAmount(i.getTotalAmount());
            return b;
        }).toList();
    }

    /**
     * V7 评价闸门：订单项归属该会员且订单已完成(40)才可评价（供商品域评价提交前校验）。
     * 裸 Boolean 返回，未命中一律 false。
     */
    @GetMapping("/order-item/can-review")
    public Boolean canReview(@RequestParam("orderItemId") Long orderItemId,
                             @RequestParam("memberId") Long memberId) {
        if (orderItemId == null || memberId == null) {
            return false;
        }
        MallOrderItem item = mallOrderItemService.getById(orderItemId);
        if (item == null) {
            return false;
        }
        MallOrder order = mallOrderService.getById(item.getOrderId());
        return order != null && memberId.equals(order.getMemberId())
                && Integer.valueOf(ShopOrderService.STATUS_FINISHED).equals(order.getStatus());
    }

    @GetMapping("/order-log")
    public List<OrderLogBrief> orderLogs(@RequestParam("orderId") Long orderId) {
        LambdaQueryWrapper<MallOrderLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallOrderLog::getOrderId, orderId).orderByAsc(MallOrderLog::getId);
        return mallOrderLogService.list(wrapper).stream().map(l -> {
            OrderLogBrief b = new OrderLogBrief();
            b.setContent(l.getContent());
            b.setOperator(l.getOperator());
            b.setOperatorType(l.getOperatorType());
            b.setCreateTime(l.getCreateTime());
            return b;
        }).toList();
    }

    @PostMapping("/order-log")
    public void appendLog(@RequestBody OrderLogAppendDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            return;
        }
        MallOrderLog log = new MallOrderLog();
        log.setOrderId(dto.getOrderId());
        log.setOrderNo(dto.getOrderNo());
        log.setContent(dto.getContent());
        log.setOperator(dto.getOperator());
        log.setOperatorType(dto.getOperatorType() == null ? 2 : dto.getOperatorType());
        mallOrderLogService.save(log);
    }

    /**
     * V8 营销玩法建单（秒杀/拼团/抽奖实物）：价格在营销域已定，这里只落单+明细+日志。
     *
     * <p>与 C 端 submit 的差异：不扣 SKU 库存（玩法库存在营销域活动维度扣）、不用券、
     * 不清购物车；订单打 order_type 标记，支付/履约/售后仍走普通订单状态机。
     */
    @PostMapping("/order/create-promo")
    public OrderBrief createPromoOrder(@RequestBody PromoOrderCreateDTO dto) {
        if (dto == null || dto.getMemberId() == null || dto.getAddressId() == null
                || dto.getSkuId() == null || dto.getPrice() == null) {
            throw new BusinessException(400, "参数错误：memberId/addressId/skuId/price 必填");
        }
        AddressBrief address = memberInnerClient.address(dto.getAddressId());
        if (address == null || !dto.getMemberId().equals(address.getMemberId())) {
            throw new BusinessException(400, "收货地址不存在");
        }
        int num = dto.getNum() == null || dto.getNum() < 1 ? 1 : dto.getNum();
        long totalAmount = dto.getPrice() * num;

        MallOrder order = new MallOrder();
        order.setOrderNo(ShopOrderService.newOrderNo());
        order.setMemberId(dto.getMemberId());
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(0L);
        order.setDiscountAmount(0L);
        order.setPayAmount(totalAmount);
        order.setStatus(ShopOrderService.STATUS_UNPAID);
        order.setPayStatus(0);
        order.setOrderType(dto.getOrderType() == null ? 0 : dto.getOrderType());
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(joinAddress(address));
        order.setBuyerRemark(dto.getBuyerRemark());
        mallOrderService.save(order);

        MallOrderItem item = new MallOrderItem();
        item.setOrderId(order.getId());
        item.setGoodsId(dto.getGoodsId());
        item.setSkuId(dto.getSkuId());
        item.setGoodsName(dto.getGoodsName());
        item.setSkuImage(dto.getSkuImage());
        item.setSpecJson(dto.getSpecJson());
        item.setPrice(dto.getPrice());
        item.setQuantity(num);
        item.setTotalAmount(totalAmount);
        mallOrderItemService.save(item);

        MallOrderLog log = new MallOrderLog();
        log.setOrderId(order.getId());
        log.setOrderNo(order.getOrderNo());
        log.setContent("营销玩法下单（order_type=" + order.getOrderType() + "），实付 " + totalAmount + " 分");
        log.setOperator("system");
        log.setOperatorType(3);
        mallOrderLogService.save(log);

        OrderBrief b = new OrderBrief();
        b.setId(order.getId());
        b.setOrderNo(order.getOrderNo());
        b.setMemberId(order.getMemberId());
        b.setMerchantId(order.getMerchantId());
        b.setStatus(order.getStatus());
        b.setPayAmount(order.getPayAmount());
        return b;
    }

    private static String joinAddress(AddressBrief a) {
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

    // ================= V16 在线 AI 客服：只读取证 =================

    /** 会员最近一笔订单（AI 未拿到订单号时的取证入口） */
    @GetMapping("/order/latest")
    public OrderBrief latestOrder(@RequestParam("memberId") Long memberId) {
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallOrder::getMemberId, memberId).orderByDesc(MallOrder::getId).last("limit 1");
        MallOrder o = mallOrderService.getOne(wrapper);
        return o == null ? null : toBrief(o);
    }

    /** 按订单号查（归属校验：不是该会员的订单返回 null，避免越权查询） */
    @GetMapping("/order/by-no")
    public OrderBrief orderByNo(@RequestParam("orderNo") String orderNo,
                                @RequestParam("memberId") Long memberId) {
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallOrder::getOrderNo, orderNo).eq(MallOrder::getMemberId, memberId).last("limit 1");
        MallOrder o = mallOrderService.getOne(wrapper);
        return o == null ? null : toBrief(o);
    }

    /** 物流轨迹（复用 trackByOrder 的归属校验，跨域调用同样不能越权） */
    @GetMapping("/delivery/track")
    public Map<String, Object> deliveryTrack(@RequestParam("orderId") Long orderId,
                                             @RequestParam("memberId") Long memberId) {
        return deliveryService.trackByOrder(orderId, memberId);
    }

    private OrderBrief toBrief(MallOrder o) {
        OrderBrief b = new OrderBrief();
        b.setId(o.getId());
        b.setOrderNo(o.getOrderNo());
        b.setMemberId(o.getMemberId());
        b.setMerchantId(o.getMerchantId());
        b.setStatus(o.getStatus());
        b.setPayAmount(o.getPayAmount());
        b.setPayOrderNo(o.getPayOrderNo());
        b.setReceiverName(o.getReceiverName());
        b.setReceiverPhone(o.getReceiverPhone());
        b.setReceiverAddress(o.getReceiverAddress());
        return b;
    }
}
