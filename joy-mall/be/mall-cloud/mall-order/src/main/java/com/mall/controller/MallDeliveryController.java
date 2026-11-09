package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.DeliveryBatchDTO;
import com.mall.dto.ShipDTO;
import com.mall.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 发货工作台（V12）：发货单台账、单笔发货（支持部分发货）、批量导入。
 */
@Tag(name = "发货单", description = "发货与部分发货")
@RestController
@RequestMapping("/mall_delivery")
public class MallDeliveryController {

    private final DeliveryService deliveryService;

    public MallDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Operation(summary = "发货单分页（订单号/快递单号/状态）")
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(deliveryService.page(keyword, status, pageNum, pageSize));
    }

    @Operation(summary = "发货单详情（包裹明细）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(deliveryService.detail(id));
    }

    @Operation(summary = "按订单号取待发明细（发货弹窗用）")
    @GetMapping("/pending")
    public Result<Map<String, Object>> pending(@RequestParam("orderNo") String orderNo) {
        return Result.ok(deliveryService.pendingByOrderNo(orderNo));
    }

    @Operation(summary = "发货：items 为空表示发剩余全部；全部发完订单转 30")
    @Log(title = "发货单", type = "INSERT")
    @PostMapping("/ship")
    public Result<Map<String, Object>> ship(@RequestBody ShipDTO dto) {
        return Result.ok(deliveryService.ship(dto, UserContext.getUsername()));
    }

    @Operation(summary = "批量发货：逐行校验，返回成功数与错误报告")
    @Log(title = "发货单", type = "INSERT")
    @PostMapping("/batch")
    public Result<Map<String, Object>> batch(@RequestBody DeliveryBatchDTO dto) {
        return Result.ok(deliveryService.batch(dto, UserContext.getUsername()));
    }
}
