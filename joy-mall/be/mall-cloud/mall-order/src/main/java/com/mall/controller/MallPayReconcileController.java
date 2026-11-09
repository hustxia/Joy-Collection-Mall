package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.ReconcileRunDTO;
import com.mall.entity.MallPayReconcile;
import com.mall.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 支付对账后台（V12）：按日拉渠道账单与本地支付单逐笔比对。
 *
 * <p>「渠道账单」当前来自入参（沙箱下等于本地镜像）；接真实渠道后由下载账单的定时任务填充。
 */
@Tag(name = "支付对账", description = "对账执行与差异处理")
@RestController
@RequestMapping("/mall_pay_reconcile")
public class MallPayReconcileController {

    private final PaymentService paymentService;

    public MallPayReconcileController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "对账差异分页")
    @GetMapping("/page")
    public Result<Page<MallPayReconcile>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Integer handleStatus,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(paymentService.reconcilePage(billDate, channel, handleStatus, pageNum, pageSize));
    }

    @Operation(summary = "执行对账（同日同渠道可重复执行，未处理差异会重建）")
    @Log(title = "支付对账", type = "OTHER")
    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestBody(required = false) ReconcileRunDTO dto) {
        return Result.ok(paymentService.runReconcile(dto == null ? new ReconcileRunDTO() : dto));
    }

    @Operation(summary = "处理差异：1确认 2忽略")
    @Log(title = "支付对账", type = "UPDATE")
    @PostMapping("/handle")
    public Result<Void> handle(@RequestBody HandleDTO dto) {
        String operator = UserContext.getUsername();
        paymentService.handleReconcile(dto.getId(), dto.getHandleStatus(), dto.getRemark(), operator);
        return Result.ok();
    }

    @lombok.Data
    public static class HandleDTO {
        private Long id;
        private Integer handleStatus;
        private String remark;
    }
}
