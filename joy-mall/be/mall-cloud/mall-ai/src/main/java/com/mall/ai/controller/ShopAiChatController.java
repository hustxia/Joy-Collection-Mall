package com.mall.ai.controller;

import com.mall.ai.dto.*;
import com.mall.ai.service.ChatService;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * C 端 AI 客服：会话、消息、流式、反馈、转人工。
 *
 * <p>全部要求会员登录（网关校验 Authorization），会话归属在 service 层再校验一次。
 */
@Tag(name = "商城前台-AI 客服", description = "会话 / 消息 / 流式 / 转人工")
@RestController
@RequestMapping("/shop/ai-chat")
public class ShopAiChatController {

    private final ChatService chatService;

    public ShopAiChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "建会话并取开场白")
    @PostMapping("/session")
    public Result<Map<String, Object>> create(@RequestBody(required = false) ChatSessionCreateDTO dto) {
        return Result.ok(chatService.createSession(requireLogin(), dto));
    }

    @Operation(summary = "发消息（同步返回完整回答）")
    @PostMapping("/message")
    public Result<ChatReplyVO> message(@RequestBody ChatMessageDTO dto) {
        return Result.ok(chatService.sendMessage(requireLogin(), dto));
    }

    @Operation(summary = "发消息（SSE 流式：meta → delta* → done）")
    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatMessageDTO dto) {
        return chatService.stream(requireLogin(), dto);
    }

    @Operation(summary = "会话历史")
    @GetMapping("/session/{id}/history")
    public Result<Map<String, Object>> history(@PathVariable("id") Long id,
                                               @RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "50") long pageSize) {
        return Result.ok(chatService.history(requireLogin(), id, pageNum, pageSize));
    }

    @Operation(summary = "消息点赞点踩")
    @PostMapping("/feedback")
    public Result<Void> feedback(@RequestBody ChatFeedbackDTO dto) {
        chatService.feedback(requireLogin(), dto);
        return Result.ok();
    }

    @Operation(summary = "转人工（建工单）")
    @PostMapping("/transfer")
    public Result<Map<String, Object>> transfer(@RequestBody ChatTransferDTO dto) {
        return Result.ok(chatService.transfer(requireLogin(), dto));
    }

    private Long requireLogin() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }
}
