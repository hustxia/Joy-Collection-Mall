package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.dto.PaymentPageDTO;
import com.mall.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 支付流水后台（V12）：支付单查询与渠道可用性。
 */
@Tag(name = "支付流水", description = "支付单台账")
@RestController
@RequestMapping("/mall_payment")
public class MallPaymentController {

    private final PaymentService paymentService;

    public MallPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "分页查询支付单（支付单号/订单号/渠道/状态）")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody PaymentPageDTO dto) {
        return Result.ok(paymentService.page(dto));
    }

    @Operation(summary = "渠道可用性：configured=false 表示未配置商户资质")
    @GetMapping("/channels")
    public Result<List<Map<String, Object>>> channels() {
        return Result.ok(paymentService.channelAvailability().entrySet().stream().map(e -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("code", e.getKey());
            row.put("configured", e.getValue());
            row.put("text", Boolean.TRUE.equals(e.getValue()) ? "可用" : "未配置");
            return row;
        }).toList());
    }
}
