package com.mall.ai.controller;

import com.mall.ai.dto.ChatQueryDTO;
import com.mall.ai.dto.FaqQueryDTO;
import com.mall.ai.dto.FaqSaveDTO;
import com.mall.ai.entity.Faq;
import com.mall.ai.service.ChatService;
import com.mall.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 后台 AI 客服：会话台账 / 会话详情 / FAQ 管理 / 用量看板 */
@Tag(name = "后台-AI 客服", description = "会话台账 / FAQ 维护 / 用量统计")
@RestController
@RequestMapping("/mall_chat")
public class MallChatController {

    private final ChatService chatService;

    public MallChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "会话分页")
    @GetMapping("/session/page")
    public Result<Map<String, Object>> sessions(ChatQueryDTO dto) {
        return Result.ok(chatService.pageSessions(dto));
    }

    @Operation(summary = "会话详情（含消息与取证轨迹）")
    @GetMapping("/session/{id}")
    public Result<Map<String, Object>> session(@PathVariable("id") Long id) {
        return Result.ok(chatService.sessionDetail(id));
    }

    @Operation(summary = "FAQ 分页")
    @GetMapping("/faq/page")
    public Result<Map<String, Object>> faqPage(FaqQueryDTO dto) {
        return Result.ok(chatService.pageFaq(dto));
    }

    @Operation(summary = "新增 FAQ")
    @PostMapping("/faq")
    public Result<Faq> createFaq(@RequestBody FaqSaveDTO dto) {
        dto.setId(null);
        return Result.ok(chatService.saveFaq(dto));
    }

    @Operation(summary = "编辑 FAQ")
    @PutMapping("/faq")
    public Result<Faq> updateFaq(@RequestBody FaqSaveDTO dto) {
        return Result.ok(chatService.saveFaq(dto));
    }

    @Operation(summary = "删除 FAQ")
    @DeleteMapping("/faq/{id}")
    public Result<Void> deleteFaq(@PathVariable("id") Long id) {
        chatService.deleteFaq(id);
        return Result.ok();
    }

    @Operation(summary = "用量与成本看板")
    @GetMapping("/stat/overview")
    public Result<Map<String, Object>> stat(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(chatService.statOverview(days));
    }
}
