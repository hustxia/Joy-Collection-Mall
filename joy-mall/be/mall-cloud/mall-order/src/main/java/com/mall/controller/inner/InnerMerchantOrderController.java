package com.mall.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.dto.ShipDTO;
import com.mall.entity.MallOrder;
import com.mall.entity.MallOrderItem;
import com.mall.service.DeliveryService;
import com.mall.service.MallOrderItemService;
import com.mall.service.MallOrderService;
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
 * 订单域商家内部端点（V11，{@code /inner/**} 免鉴权）。
 *
 * <p>商家后台的订单页/发货按钮最终都落到这里。发货<b>直接委派</b>
 * {@link DeliveryService#ship}，部分发货、单号唯一、全发完转 30 全部沿用 V12 那一套，
 * 商家端与平台端的订单状态天然一致（验收第 3 条）。
 */
@RestController
@RequestMapping("/inner/merchant")
public class InnerMerchantOrderController {

    private final MallOrderService orderService;
    private final MallOrderItemService orderItemService;
    private final DeliveryService deliveryService;

    public InnerMerchantOrderController(MallOrderService orderService, MallOrderItemService orderItemService,
                                        DeliveryService deliveryService) {
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.deliveryService = deliveryService;
    }

    @GetMapping("/order/page")
    public Map<String, Object> page(@RequestParam("merchantId") Long merchantId,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "status", required = false) Integer status,
                                    @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        requireMerchant(merchantId);
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMerchantId, merchantId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(MallOrder::getOrderNo, kw)
                    .or().like(MallOrder::getReceiverName, kw)
                    .or().like(MallOrder::getReceiverPhone, kw));
        }
        if (status != null) {
            wrapper.eq(MallOrder::getStatus, status);
        }
        wrapper.orderByDesc(MallOrder::getId);
        Page<MallOrder> page = orderService.page(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }

    @GetMapping("/order/{id}")
    public Map<String, Object> detail(@PathVariable("id") Long id,
                                      @RequestParam("merchantId") Long merchantId) {
        requireMerchant(merchantId);
        MallOrder order = orderService.getById(id);
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (order.getMerchantId() == null || !order.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权访问其他店铺的订单");
        }
        List<MallOrderItem> items = orderItemService.list(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, id)
                .orderByAsc(MallOrderItem::getId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", order);
        data.put("items", items);
        return data;
    }

    /** 发货：body 与 ShipDTO 同构，items 为空表示把剩余未发数量全部发出 */
    @PostMapping("/order/{id}/deliver")
    public Map<String, Object> deliver(@PathVariable("id") Long id,
                                       @RequestParam("merchantId") Long merchantId,
                                       @RequestParam(value = "operator", required = false) String operator,
                                       @RequestBody(required = false) ShipDTO dto) {
        requireMerchant(merchantId);
        MallOrder order = orderService.getById(id);
        if (order == null) {
            throw new BusinessException(400, "订单不存在");
        }
        if (order.getMerchantId() == null || !order.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权操作其他店铺的订单");
        }
        ShipDTO ship = dto == null ? new ShipDTO() : dto;
        // 路径即权威：防止请求体里塞别的 orderId 把别人的单发了
        ship.setOrderId(id);
        return deliveryService.ship(ship, operator == null || operator.isBlank() ? "merchant" : operator);
    }

    private void requireMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new BusinessException(400, "参数错误：merchantId 必填");
        }
    }
}
