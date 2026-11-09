package com.mall.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.ai.dto.*;
import com.mall.ai.entity.*;
import com.mall.ai.mapper.*;
import com.mall.ai.service.ChatService;
import com.mall.ai.support.*;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.SupportInnerClient;
import com.mall.common.feign.dto.TicketInnerCreateDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 客服实现。
 *
 * <p>几条硬约束：
 * <ul>
 *   <li>会话归属校验：所有 C 端方法先校验 {@code session.memberId == 当前会员}，越权 403；</li>
 *   <li>限流：按会员「每分钟 + 每天」双阈值，超限 429——防止脚本刷 token 成本；</li>
 *   <li>SSE 首帧：建连立即推 meta，避免 SseEmitter 懒提交导致客户端干等（坑库 #22）；</li>
 *   <li>转人工：工单建单失败不吞异常，但会话状态照常切换，前端能看到「已转人工」。</li>
 * </ul>
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final FaqMapper faqMapper;
    private final ChatFeedbackMapper feedbackMapper;
    private final AiStatDailyMapper statMapper;
    private final ChatOrchestrator orchestrator;
    private final FaqRetriever faqRetriever;
    private final ToolGateway toolGateway;
    private final SupportInnerClient supportInnerClient;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.ai.max-message-chars:500}")
    private int maxMessageChars;

    @Value("${app.ai.rate-limit-per-minute:12}")
    private int limitPerMinute;

    @Value("${app.ai.rate-limit-per-day:200}")
    private int limitPerDay;

    @Value("${app.ai.transfer-after-fallback:2}")
    private int transferAfterFallback;

    /** 限流窗口：会员 → 最近一分钟的调用时间戳 */
    private final Map<Long, Deque<Long>> minuteWindow = new ConcurrentHashMap<>();
    /** 日计数：会员 → [日期, 次数] */
    private final Map<Long, long[]> dayCount = new ConcurrentHashMap<>();

    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ai-sse");
        t.setDaemon(true);
        return t;
    });

    public ChatServiceImpl(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                           FaqMapper faqMapper, ChatFeedbackMapper feedbackMapper, AiStatDailyMapper statMapper,
                           ChatOrchestrator orchestrator, FaqRetriever faqRetriever,
                           ToolGateway toolGateway, SupportInnerClient supportInnerClient) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.faqMapper = faqMapper;
        this.feedbackMapper = feedbackMapper;
        this.statMapper = statMapper;
        this.orchestrator = orchestrator;
        this.faqRetriever = faqRetriever;
        this.toolGateway = toolGateway;
        this.supportInnerClient = supportInnerClient;
    }

    // ==================== C 端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSession(Long memberId, ChatSessionCreateDTO dto) {
        ChatSession session = new ChatSession();
        session.setMemberId(memberId);
        session.setChannel(dto != null && dto.getChannel() != null ? dto.getChannel() : "pc");
        session.setStatus(ChatSession.STATUS_BOT);
        session.setSource(dto == null ? null : dto.getSource());
        session.setMsgCount(1);
        session.setLastActiveTime(LocalDateTime.now());
        sessionMapper.insert(session);

        String greeting = "你好，我是悦集商城的 AI 客服助手。可以帮你查订单物流、退款进度，"
                + "也能解答运费、售后、优惠券这些规则问题。需要人工的话，直接说「转人工」就行。";
        ChatMessage hello = persistMessage(session, memberId, ChatMessage.ROLE_AI, "text", greeting,
                null, "greeting", null, false, "rule", 0, 0);
        accumulate(1, 1, 0, 0, 0, 0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", String.valueOf(session.getId()));
        data.put("greeting", toVO(hello));
        data.put("suggestions", faqRetriever.suggestions(null, 3));
        data.put("status", session.getStatus());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatReplyVO sendMessage(Long memberId, ChatMessageDTO dto) {
        checkRateLimit(memberId);
        ChatSession session = requireSession(memberId, dto.getSessionId());
        String text = requireText(dto.getContent());

        List<ChatMessage> history = recentMessages(session.getId(), 12);
        persistMessage(session, memberId, ChatMessage.ROLE_USER, "text", text, null, null,
                null, false, null, 0, 0);

        ChatReplyVO reply;
        try {
            reply = orchestrator.reply(session, text, history);
        } catch (Exception e) {
            log.error("AI 编排失败，走兜底", e);
            reply = new ChatReplyVO();
            reply.setSessionId(String.valueOf(session.getId()));
            reply.setContent("抱歉，我这边暂时没法查到这个问题的准确信息，可以帮你转人工处理。");
            reply.setFallback(true);
            reply.setCanTransfer(true);
            reply.setProvider("system");
            reply.setIntent("error");
            reply.setSuggestions(faqRetriever.suggestions(null, 3));
        }

        ChatMessage aiMessage = persistMessage(session, memberId, ChatMessage.ROLE_AI, reply.getMsgType(),
                reply.getContent(), reply.getCards(), reply.getIntent(), reply.getToolTrace(),
                reply.isFallback(), reply.getProvider(), reply.getTokens(), reply.getLatencyMs());
        reply.setMessageId(String.valueOf(aiMessage.getId()));

        session.setMsgCount(session.getMsgCount() == null ? 2 : session.getMsgCount() + 2);
        session.setIntent(reply.getIntent());
        session.setLastActiveTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        accumulate(0, 2, 1, 0, reply.isFallback() ? 1 : 0, reply.getTokens());
        return reply;
    }

    @Override
    public SseEmitter stream(Long memberId, ChatMessageDTO dto) {
        checkRateLimit(memberId);
        ChatSession session = requireSession(memberId, dto.getSessionId());
        String text = requireText(dto.getContent());

        SseEmitter emitter = new SseEmitter(60_000L);
        // 坑库 #22：先推首帧，立即提交响应头，否则客户端干等
        try {
            emitter.send(SseEmitter.event().name("meta")
                    .data(Map.of("sessionId", String.valueOf(session.getId()), "typing", true)));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        sseExecutor.execute(() -> {
            try {
                List<ChatMessage> history = recentMessages(session.getId(), 12);
                persistMessage(session, memberId, ChatMessage.ROLE_USER, "text", text, null, null,
                        null, false, null, 0, 0);

                ChatReplyVO reply;
                try {
                    reply = orchestrator.reply(session, text, history);
                } catch (Exception e) {
                    log.error("AI 编排失败（stream）", e);
                    reply = new ChatReplyVO();
                    reply.setSessionId(String.valueOf(session.getId()));
                    reply.setContent("抱歉，我暂时没法查到准确信息，可以帮你转人工处理。");
                    reply.setFallback(true);
                    reply.setCanTransfer(true);
                    reply.setProvider("system");
                }

                for (String piece : orchestrator.chunk(reply.getContent())) {
                    emitter.send(SseEmitter.event().name("delta").data(piece));
                    Thread.sleep(16);
                }
                ChatMessage aiMessage = persistMessage(session, memberId, ChatMessage.ROLE_AI, reply.getMsgType(),
                        reply.getContent(), reply.getCards(), reply.getIntent(), reply.getToolTrace(),
                        reply.isFallback(), reply.getProvider(), reply.getTokens(), reply.getLatencyMs());
                reply.setMessageId(String.valueOf(aiMessage.getId()));

                session.setMsgCount(session.getMsgCount() == null ? 2 : session.getMsgCount() + 2);
                session.setIntent(reply.getIntent());
                session.setLastActiveTime(LocalDateTime.now());
                sessionMapper.updateById(session);
                accumulate(0, 2, 1, 0, reply.isFallback() ? 1 : 0, reply.getTokens());

                Map<String, Object> done = new LinkedHashMap<>();
                done.put("messageId", reply.getMessageId());
                done.put("msgType", reply.getMsgType());
                done.put("cards", reply.getCards());
                done.put("suggestions", reply.getSuggestions());
                done.put("canTransfer", reply.isCanTransfer());
                done.put("fallback", reply.isFallback());
                done.put("intent", reply.getIntent());
                emitter.send(SseEmitter.event().name("done").data(done));
                emitter.complete();
            } catch (Exception e) {
                log.warn("SSE 推送中断：{}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @Override
    public Map<String, Object> history(Long memberId, Long sessionId, long pageNum, long pageSize) {
        ChatSession session = requireSession(memberId, sessionId);
        Page<ChatMessage> page = new Page<>(pageNum, Math.min(pageSize, 100));
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId).orderByAsc(ChatMessage::getId);
        Page<ChatMessage> result = messageMapper.selectPage(page, wrapper);

        Map<String, Object> sessionInfo = new LinkedHashMap<>();
        sessionInfo.put("sessionId", String.valueOf(session.getId()));
        sessionInfo.put("status", session.getStatus());
        sessionInfo.put("ticketId", session.getTicketId() == null ? null : String.valueOf(session.getTicketId()));
        sessionInfo.put("satisfaction", session.getSatisfaction());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords().stream().map(this::toVO).toList());
        data.put("total", result.getTotal());
        data.put("session", sessionInfo);
        return data;
    }

    @Override
    public void feedback(Long memberId, ChatFeedbackDTO dto) {
        ChatMessage message = messageMapper.selectById(dto.getMessageId());
        if (message == null || !memberId.equals(message.getMemberId())) {
            throw new BusinessException(403, "消息不存在或无权操作");
        }
        String rating = "down".equalsIgnoreCase(dto.getRating()) ? "down" : "up";
        LambdaQueryWrapper<ChatFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatFeedback::getMessageId, dto.getMessageId()).eq(ChatFeedback::getMemberId, memberId);
        ChatFeedback exists = feedbackMapper.selectOne(wrapper);
        if (exists == null) {
            ChatFeedback fb = new ChatFeedback();
            fb.setMessageId(dto.getMessageId());
            fb.setSessionId(message.getSessionId());
            fb.setMemberId(memberId);
            fb.setRating(rating);
            fb.setReason(dto.getReason());
            feedbackMapper.insert(fb);
        } else {
            exists.setRating(rating);
            exists.setReason(dto.getReason());
            feedbackMapper.updateById(exists);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transfer(Long memberId, ChatTransferDTO dto) {
        ChatSession session = requireSession(memberId, dto.getSessionId());
        if (session.getTicketId() != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ticketId", String.valueOf(session.getTicketId()));
            data.put("alreadyTransferred", true);
            data.put("status", session.getStatus());
            return data;
        }
        List<ChatMessage> messages = recentMessages(session.getId(), 20);
        Evidence evidence = toolGateway.collect(Intent.ORDER, null, memberId);
        String summary = orchestrator.summarize(messages, evidence);

        String firstAsk = messages.stream()
                .filter(m -> ChatMessage.ROLE_USER.equals(m.getRole()))
                .map(ChatMessage::getContent)
                .filter(Objects::nonNull)
                .findFirst().orElse("AI 客服转接");

        TicketInnerCreateDTO ticket = new TicketInnerCreateDTO();
        ticket.setMemberId(memberId);
        ticket.setCategory(categoryOf(session.getIntent()));
        ticket.setPriority(2);
        ticket.setTitle("AI 转接：" + cut(firstAsk, 40));
        ticket.setContent(cut(summary + (dto.getReason() == null ? "" : "\n会员补充：" + dto.getReason()), 1900));
        if (evidence.hasOrder()) {
            ticket.setOrderId(evidence.getOrder().getId());
            ticket.setOrderNo(evidence.getOrder().getOrderNo());
        }

        Map<String, Object> created;
        try {
            created = supportInnerClient.createTicket(ticket);
        } catch (Exception e) {
            log.error("转人工建单失败", e);
            throw new BusinessException(503, "转人工暂时不可用，请稍后在「客服工单」里提交");
        }
        if (created == null || created.get("id") == null) {
            throw new BusinessException(503, "转人工暂时不可用，请稍后重试");
        }

        Long ticketId = Long.valueOf(String.valueOf(created.get("id")));
        session.setStatus(ChatSession.STATUS_TRANSFERRED);
        session.setTicketId(ticketId);
        session.setLastActiveTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "transfer");
        card.put("title", "已转人工 · 工单 " + created.get("ticketNo"));
        card.put("fields", List.of(Map.of("label", "工单号", "value", String.valueOf(created.get("ticketNo"))),
                Map.of("label", "当前状态", "value", "待客服处理")));
        card.put("action", Map.of("label", "查看我的工单", "url", "/tickets"));
        persistMessage(session, memberId, ChatMessage.ROLE_SYSTEM, "transfer_card",
                "已为你转接人工客服，工单号 " + created.get("ticketNo") + "，客服回复后会通知你。",
                List.of(card), "transfer", null, false, "rule", 0, 0);
        accumulate(0, 1, 0, 1, 0, 0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketId", String.valueOf(ticketId));
        data.put("ticketNo", String.valueOf(created.get("ticketNo")));
        data.put("status", session.getStatus());
        return data;
    }

    // ==================== 后台 ====================

    @Override
    public Map<String, Object> pageSessions(ChatQueryDTO dto) {
        Page<ChatSession> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, ChatSession::getStatus, dto.getStatus())
                .eq(dto.getIntent() != null && !dto.getIntent().isBlank(), ChatSession::getIntent, dto.getIntent())
                .orderByDesc(ChatSession::getLastActiveTime);
        Page<ChatSession> result = sessionMapper.selectPage(page, wrapper);

        List<ChatSessionVO> rows = new ArrayList<>();
        for (ChatSession s : result.getRecords()) {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(String.valueOf(s.getId()));
            vo.setMemberId(String.valueOf(s.getMemberId()));
            vo.setChannel(s.getChannel());
            vo.setStatus(s.getStatus());
            vo.setSource(s.getSource());
            vo.setTicketId(s.getTicketId() == null ? null : String.valueOf(s.getTicketId()));
            vo.setSatisfaction(s.getSatisfaction());
            vo.setIntent(s.getIntent());
            vo.setMsgCount(s.getMsgCount());
            vo.setLastActiveTime(s.getLastActiveTime());
            vo.setCreateTime(s.getCreateTime());
            vo.setSummary(summaryOf(s.getId()));
            rows.add(vo);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", rows);
        data.put("total", result.getTotal());
        data.put("pageNum", dto.getPageNum());
        data.put("pageSize", dto.getPageSize());
        return data;
    }

    @Override
    public Map<String, Object> sessionDetail(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId).orderByAsc(ChatMessage::getId);
        List<ChatMessageVO> messages = messageMapper.selectList(wrapper).stream().map(this::toVO).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", String.valueOf(session.getId()));
        data.put("memberId", String.valueOf(session.getMemberId()));
        data.put("status", session.getStatus());
        data.put("intent", session.getIntent());
        data.put("ticketId", session.getTicketId() == null ? null : String.valueOf(session.getTicketId()));
        data.put("messages", messages);
        return data;
    }

    @Override
    public Map<String, Object> pageFaq(FaqQueryDTO dto) {
        Page<Faq> page = new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100));
        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.and(w -> w.like(Faq::getQuestion, dto.getKeyword()).or().like(Faq::getAnswer, dto.getKeyword()));
        }
        wrapper.eq(dto.getCategory() != null && !dto.getCategory().isBlank(), Faq::getCategory, dto.getCategory())
                .eq(dto.getStatus() != null, Faq::getStatus, dto.getStatus())
                .orderByAsc(Faq::getId);
        Page<Faq> result = faqMapper.selectPage(page, wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("categories", faqRetriever.categories());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Faq saveFaq(FaqSaveDTO dto) {
        if (dto.getQuestion() == null || dto.getQuestion().isBlank()
                || dto.getAnswer() == null || dto.getAnswer().isBlank()) {
            throw new BusinessException(400, "问题与答案不能为空");
        }
        Faq faq;
        if (dto.getId() != null) {
            faq = faqMapper.selectById(dto.getId());
            if (faq == null) {
                throw new BusinessException(404, "FAQ 不存在");
            }
        } else {
            faq = new Faq();
            faq.setCode(dto.getCode() == null || dto.getCode().isBlank()
                    ? "faq.custom." + IdWorker.getId() : dto.getCode());
            faq.setHitCount(0L);
        }
        faq.setQuestion(dto.getQuestion());
        faq.setAnswer(dto.getAnswer());
        faq.setCategory(dto.getCategory() == null || dto.getCategory().isBlank() ? "其他" : dto.getCategory());
        faq.setSynonyms(dto.getSynonyms());
        faq.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        if (dto.getId() != null) {
            faqMapper.updateById(faq);
        } else {
            faqMapper.insert(faq);
        }
        faqRetriever.invalidate();
        return faq;
    }

    @Override
    public void deleteFaq(Long id) {
        faqMapper.deleteById(id);
        faqRetriever.invalidate();
    }

    @Override
    public Map<String, Object> statOverview(int days) {
        int span = Math.max(1, Math.min(days, 30));
        LocalDate from = LocalDate.now().minusDays(span - 1L);
        LambdaQueryWrapper<AiStatDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AiStatDaily::getStatDate, from).orderByAsc(AiStatDaily::getStatDate);
        List<AiStatDaily> rows = statMapper.selectList(wrapper);

        long sessions = rows.stream().mapToLong(r -> nz(r.getSessionCount())).sum();
        long messages = rows.stream().mapToLong(r -> nz(r.getMessageCount())).sum();
        long aiReplies = rows.stream().mapToLong(r -> nz(r.getAiReplyCount())).sum();
        long transfers = rows.stream().mapToLong(r -> nz(r.getTransferCount())).sum();
        long fallbacks = rows.stream().mapToLong(r -> nz(r.getFallbackCount())).sum();
        long tokens = rows.stream().mapToLong(r -> r.getTokens() == null ? 0 : r.getTokens()).sum();

        // 补零：图上要连续日期
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate d = from.plusDays(i);
            AiStatDaily hit = rows.stream().filter(r -> d.equals(r.getStatDate())).findFirst().orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("sessionCount", hit == null ? 0 : nz(hit.getSessionCount()));
            row.put("messageCount", hit == null ? 0 : nz(hit.getMessageCount()));
            row.put("transferCount", hit == null ? 0 : nz(hit.getTransferCount()));
            row.put("tokens", hit == null ? 0 : hit.getTokens());
            daily.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionCount", sessions);
        data.put("messageCount", messages);
        data.put("aiReplyCount", aiReplies);
        data.put("transferCount", transfers);
        data.put("fallbackCount", fallbacks);
        data.put("tokens", tokens);
        // 客服口径：自助解决 = 会话数 - 转人工数
        data.put("selfSolveRate", sessions == 0 ? 0 : Math.round((sessions - transfers) * 1000.0 / sessions) / 10.0);
        data.put("transferRate", sessions == 0 ? 0 : Math.round(transfers * 1000.0 / sessions) / 10.0);
        // 成本估算：按 ¥1.5/百万 token 的量级折算（仅看板参考）
        data.put("costEstimate", Math.round(tokens / 1_000_000.0 * 1.5 * 100) / 100.0);
        data.put("daily", daily);
        data.put("topFaq", faqRetriever.top(5));
        data.put("categories", faqRetriever.categories());
        return data;
    }

    // ==================== 内部工具方法 ====================

    private void checkRateLimit(Long memberId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = minuteWindow.computeIfAbsent(memberId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) {
                window.pollFirst();
            }
            if (window.size() >= limitPerMinute) {
                throw new BusinessException(429, "问得有点快，休息一下再试～");
            }
            window.addLast(now);
        }
        long today = LocalDate.now().toEpochDay();
        long[] counter = dayCount.computeIfAbsent(memberId, k -> new long[]{today, 0});
        synchronized (counter) {
            if (counter[0] != today) {
                counter[0] = today;
                counter[1] = 0;
            }
            if (counter[1] >= limitPerDay) {
                throw new BusinessException(429, "今天的咨询次数已用完，可以转人工继续");
            }
            counter[1]++;
        }
    }

    private ChatSession requireSession(Long memberId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(400, "缺少 sessionId");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !memberId.equals(session.getMemberId())) {
            throw new BusinessException(403, "会话不存在或无权访问");
        }
        return session;
    }

    private String requireText(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "说点什么吧");
        }
        String text = content.trim();
        return text.length() > maxMessageChars ? text.substring(0, maxMessageChars) : text;
    }

    private List<ChatMessage> recentMessages(Long sessionId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId).orderByDesc(ChatMessage::getId).last("limit " + limit);
        List<ChatMessage> list = new ArrayList<>(messageMapper.selectList(wrapper));
        Collections.reverse(list);
        return list;
    }

    private ChatMessage persistMessage(ChatSession session, Long memberId, String role, String msgType,
                                       String content, List<Map<String, Object>> cards, String intent,
                                       String toolTrace, boolean fallback, String provider,
                                       int tokens, int latencyMs) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setMemberId(memberId);
        message.setRole(role);
        message.setMsgType(msgType == null ? "text" : msgType);
        message.setContent(content);
        message.setCards(cards == null || cards.isEmpty() ? null : writeJson(cards));
        message.setIntent(intent);
        message.setToolTrace(toolTrace);
        message.setFallback(fallback ? 1 : 0);
        message.setProvider(provider);
        message.setTokens(tokens);
        message.setLatencyMs(latencyMs);
        messageMapper.insert(message);
        return message;
    }

    private ChatMessageVO toVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(String.valueOf(message.getId()));
        vo.setRole(message.getRole());
        vo.setMsgType(message.getMsgType());
        vo.setContent(message.getContent());
        vo.setCards(readCards(message.getCards()));
        vo.setFallback(message.getFallback() != null && message.getFallback() == 1);
        vo.setProvider(message.getProvider());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private List<Map<String, Object>> readCards(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(jsonText, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private String summaryOf(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getRole, ChatMessage.ROLE_USER)
                .orderByAsc(ChatMessage::getId).last("limit 1");
        ChatMessage first = messageMapper.selectOne(wrapper);
        return first == null ? "" : cut(first.getContent(), 60);
    }

    private int categoryOf(String intent) {
        if (intent == null) {
            return 5;
        }
        return switch (intent) {
            case "refund" -> 2;
            case "logistics" -> 3;
            case "order" -> 2;
            case "faq", "chitchat" -> 1;
            default -> 5;
        };
    }

    private void accumulate(int session, int message, int reply, int transfer, int fallback, long tokens) {
        try {
            statMapper.accumulate(IdWorker.getId(), session, message, reply, transfer, fallback, tokens);
        } catch (Exception e) {
            log.debug("stat accumulate failed: {}", e.getMessage());
        }
    }

    private String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private long nz(Integer v) {
        return v == null ? 0 : v;
    }
}
