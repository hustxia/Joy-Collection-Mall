package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端物流（V12）：按订单查全部包裹与轨迹节点。
 */
@Tag(name = "商城前台-物流", description = "包裹与轨迹")
@RestController
@RequestMapping("/shop/delivery")
public class ShopDeliveryController {

    private final DeliveryService deliveryService;

    public ShopDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Operation(summary = "订单包裹与物流轨迹（无发货返回空包裹 + 提示）")
    @GetMapping("/track/{orderId}")
    public Result<Map<String, Object>> track(@PathVariable("orderId") Long orderId) {
        return Result.ok(deliveryService.trackByOrder(orderId, UserContext.getUserId()));
    }
}
