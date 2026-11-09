package com.mall.ai.service;

import com.mall.ai.dto.*;
import com.mall.ai.entity.Faq;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** AI 客服领域服务 */
public interface ChatService {

    /** 建会话并返回开场白 */
    Map<String, Object> createSession(Long memberId, ChatSessionCreateDTO dto);

    /** 发消息（同步返回完整回答） */
    ChatReplyVO sendMessage(Long memberId, ChatMessageDTO dto);

    /** 发消息（SSE 流式：先推 meta，再逐段推 delta，最后 done） */
    SseEmitter stream(Long memberId, ChatMessageDTO dto);

    /** 历史消息 */
    Map<String, Object> history(Long memberId, Long sessionId, long pageNum, long pageSize);

    /** 消息反馈 */
    void feedback(Long memberId, ChatFeedbackDTO dto);

    /** 转人工：建工单并回写会话 */
    Map<String, Object> transfer(Long memberId, ChatTransferDTO dto);

    // ---------- 后台 ----------
    Map<String, Object> pageSessions(ChatQueryDTO dto);

    Map<String, Object> sessionDetail(Long sessionId);

    Map<String, Object> pageFaq(FaqQueryDTO dto);

    Faq saveFaq(FaqSaveDTO dto);

    void deleteFaq(Long id);

    Map<String, Object> statOverview(int days);
}
