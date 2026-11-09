package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.InvoicePageDTO;
import com.mall.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 发票管理后台（V12）：开票队列、上传电子发票开票、红冲。
 */
@Tag(name = "发票管理", description = "开票与红冲")
@RestController
@RequestMapping("/mall_invoice")
public class MallInvoiceController {

    private final InvoiceService invoiceService;

    public MallInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Operation(summary = "发票申请分页（订单号/申请单号/抬头/状态）")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody InvoicePageDTO dto) {
        return Result.ok(invoiceService.adminPage(dto));
    }

    @Operation(summary = "开票：上传电子发票文件地址后置已开票")
    @Log(title = "发票管理", type = "UPDATE")
    @PostMapping("/issue")
    public Result<Void> issue(@RequestBody IssueDTO dto) {
        invoiceService.issue(dto.getId(), dto.getFileUrl());
        return Result.ok();
    }

    @Operation(summary = "红冲：已开票置已红冲，原票失效")
    @Log(title = "发票管理", type = "UPDATE")
    @PostMapping("/red-flush")
    public Result<Void> redFlush(@RequestBody IssueDTO dto) {
        invoiceService.redFlush(dto.getId(), dto.getRemark());
        return Result.ok();
    }

    @Data
    public static class IssueDTO {
        private Long id;
        private String fileUrl;
        private String remark;
    }
}
