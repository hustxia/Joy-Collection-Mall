package com.mall.controller;

import com.mall.common.result.Result;
import com.mall.dto.TicketPageDTO;
import com.mall.dto.TicketReplyDTO;
import com.mall.dto.TicketStatusDTO;
import com.mall.service.MallTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台客服工单（V14，REQ-P1-08）：列表 / 回复 / 改状态 / 分类统计。
 *
 * <p>超时为「查询时惰性置位」：进列表即把超 24h 未首响的工单刷成 {@code timeout_flag=1}，
 * 不依赖定时任务（调度器挂了也不漏标），口径与 C 端 {@code overdue} 字段一致。
 */
@Tag(name = "客服工单", description = "工单处理与统计")
@RestController
@RequestMapping("/mall_ticket")
public class MallTicketController {

    private final MallTicketService ticketService;

    public MallTicketController(MallTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "工单分页（状态/分类/关键字/只看超时）")
    @PostMapping("/page")
    public Result<Map<String, Object>> page(@RequestBody TicketPageDTO dto) {
        return Result.ok(ticketService.adminPage(dto));
    }

    @Operation(summary = "工单详情（含回复）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        // 后台不校验归属：客服要能看全部工单
        return Result.ok(ticketService.detail(id, null));
    }

    @Operation(summary = "客服回复（首响会解除超时标记）")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable("id") Long id, @RequestBody TicketReplyDTO dto) {
        ticketService.agentReply(id, com.mall.common.context.UserContext.getUserId(),
                dto == null ? null : dto.getContent());
        return Result.ok();
    }

    @Operation(summary = "变更工单状态（1待处理2已回复3已关闭）")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody TicketStatusDTO dto) {
        ticketService.updateStatus(id, dto == null ? null : dto.getStatus());
        return Result.ok();
    }

    @Operation(summary = "分类统计（总数/待处理/超时）")
    @GetMapping("/stat")
    public Result<List<Map<String, Object>>> stat() {
        return Result.ok(ticketService.stat());
    }
}
