package com.mall.controller;

import com.mall.common.context.MerchantContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.merchant.feign.MerchantOrderClient;
import com.mall.merchant.support.MerchantGuard;
import com.mall.merchant.support.MerchantResults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商家订单自助（V11）：列表 / 详情 / 发货。
 *
 * <p>发货一律转给订单域 {@code DeliveryService.ship(...)}：部分发货、快递单号唯一、
 * 全部发完才转 30 这些规则只允许有一套实现，商家端不能另起炉灶。
 */
@Tag(name = "商家后台-订单", description = "商家订单查询与发货")
@RestController
@RequestMapping("/merchant-admin/order")
public class MerchantOrderController {

    private final MerchantOrderClient orderClient;
    private final MerchantGuard guard;

    public MerchantOrderController(MerchantOrderClient orderClient, MerchantGuard guard) {
        this.orderClient = orderClient;
        this.guard = guard;
    }

    @Operation(summary = "本店订单分页（单号/收货人模糊，状态可选）")
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        Long merchantId = guard.currentMerchantId();
        return Result.ok(MerchantResults.unwrap(
                orderClient.page(merchantId, keyword, status, pageNum, pageSize)));
    }

    @Operation(summary = "订单详情（含明细）；非本店订单 403")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        guard.currentMerchantId();
        if (!guard.assertOwned("mall_order", id, "查看订单")) {
            throw new BusinessException(400, "订单不存在");
        }
        return Result.ok(MerchantResults.unwrap(orderClient.detail(id, MerchantContext.get())));
    }

    @Operation(summary = "发货：items 为空表示把剩余未发数量全部发出")
    @Log(title = "商家订单", type = "UPDATE")
    @PostMapping("/{id}/deliver")
    public Result<Map<String, Object>> deliver(@PathVariable("id") Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        guard.currentMerchantId();
        if (!guard.assertOwned("mall_order", id, "订单发货")) {
            throw new BusinessException(400, "订单不存在");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (body != null) {
            payload.putAll(body);
        }
        // 路径上的 id 才是权威值，避免请求体与路径不一致时发错单
        payload.put("orderId", id);
        String operator = MerchantContext.getUsername();
        return Result.ok(MerchantResults.unwrap(
                orderClient.deliver(id, MerchantContext.get(), operator, payload)));
    }
}
