package com.mall.controller.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.dto.InvoiceApplyDTO;
import com.mall.entity.MallInvoice;
import com.mall.mapper.MallInvoiceMapper;
import com.mall.service.InvoiceService;
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
 * C 端发票（V12）：申请、我的发票、按订单查状态。
 */
@Tag(name = "商城前台-发票", description = "开票申请与查询")
@RestController
@RequestMapping("/shop/invoice")
public class ShopInvoiceController {

    private final InvoiceService invoiceService;
    private final MallInvoiceMapper invoiceMapper;

    public ShopInvoiceController(InvoiceService invoiceService, MallInvoiceMapper invoiceMapper) {
        this.invoiceService = invoiceService;
        this.invoiceMapper = invoiceMapper;
    }

    @Operation(summary = "申请开票：订单须已完成，一单一票，金额取实付")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody InvoiceApplyDTO dto) {
        MallInvoice invoice = invoiceService.apply(UserContext.getUserId(), dto);
        return Result.ok(Map.of("invoiceNo", invoice.getInvoiceNo(), "amount", invoice.getAmount(),
                "status", invoice.getStatus()));
    }

    @Operation(summary = "我的发票")
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(invoiceService.pageOfMember(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "某订单的发票（无则返回 null，前端据此显示申请按钮）")
    @GetMapping("/order/{orderId}")
    public Result<MallInvoice> byOrder(@PathVariable("orderId") Long orderId) {
        MallInvoice invoice = invoiceMapper.selectOne(new LambdaQueryWrapper<MallInvoice>()
                .eq(MallInvoice::getOrderId, orderId)
                .eq(MallInvoice::getMemberId, UserContext.getUserId())
                .last("LIMIT 1"));
        return Result.ok(invoice);
    }
}
