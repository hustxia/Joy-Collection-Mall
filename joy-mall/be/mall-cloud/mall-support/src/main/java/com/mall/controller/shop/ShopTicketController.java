package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.dto.TicketCreateDTO;
import com.mall.dto.TicketReplyDTO;
import com.mall.service.MallTicketService;
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
 * C 端客服工单（V14，REQ-P1-08）：提交 / 我的工单 / 详情 / 追加留言。
 *
 * <p>会员身份只认会话（{@link UserContext}），前端传的 memberId 一律忽略；
 * 详情与留言都做归属校验，越权返回 403。
 */
@Tag(name = "商城前台-客服工单", description = "工单提交与留言")
@RestController
@RequestMapping("/shop/ticket")
public class ShopTicketController {

    private final MallTicketService ticketService;

    public ShopTicketController(MallTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "提交工单（可关联订单）")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody TicketCreateDTO dto) {
        return Result.ok(ticketService.create(requireLogin(), dto));
    }

    @Operation(summary = "我的工单分页")
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(ticketService.memberPage(requireLogin(), pageNum, pageSize));
    }

    @Operation(summary = "工单详情（含往来回复）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(ticketService.detail(id, requireLogin()));
    }

    @Operation(summary = "会员追加留言")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable("id") Long id, @RequestBody TicketReplyDTO dto) {
        ticketService.memberReply(id, requireLogin(), dto == null ? null : dto.getContent());
        return Result.ok();
    }

    private Long requireLogin() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }
}
