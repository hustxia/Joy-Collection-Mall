package com.mall.ai;

import com.mall.ai.dto.ChatReplyVO;
import com.mall.ai.entity.ChatSession;
import com.mall.ai.llm.LlmProvider;
import com.mall.ai.llm.LlmRouter;
import com.mall.ai.support.ChatOrchestrator;
import com.mall.ai.support.FaqRetriever;
import com.mall.ai.support.Guard;
import com.mall.ai.support.IntentClassifier;
import com.mall.ai.support.ToolGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 无 DB、无网络的编排链路测试：mock 掉 Feign 与 FAQ 库，
 * 验证「LLM 不产生事实」这条主线在纯代码层成立。
 */
class MockPipelineTest {

    @SuppressWarnings("unchecked")
    private ChatOrchestrator orchestrator(com.mall.common.feign.dto.OrderBrief order,
                                          com.mall.ai.entity.Faq faq) {
        com.mall.common.feign.OrderInnerClient orderClient = mock(com.mall.common.feign.OrderInnerClient.class);
        com.mall.common.feign.RefundInnerClient refundClient = mock(com.mall.common.feign.RefundInnerClient.class);
        if (order != null) {
            when(orderClient.latestOrder(any(Long.class))).thenReturn(order);
            when(orderClient.orderItems(any(Long.class))).thenReturn(List.of());
        }
        com.mall.ai.mapper.FaqMapper faqMapper = mock(com.mall.ai.mapper.FaqMapper.class);
        when(faqMapper.selectList(any())).thenReturn(faq == null ? List.of() : List.of(faq));
        FaqRetriever retriever = new FaqRetriever(faqMapper);
        retriever.invalidate();

        LlmRouter router = new LlmRouter(List.of((LlmProvider) new com.mall.ai.llm.MockLlmProvider()), "mock");
        return new ChatOrchestrator(new IntentClassifier(),
                new ToolGateway(orderClient, refundClient), retriever, new Guard(), router);
    }

    private ChatSession session() {
        ChatSession s = new ChatSession();
        s.setId(1L);
        s.setMemberId(100L);
        return s;
    }

    @Test
    void 有订单证据时回答引用真实订单号() {
        com.mall.common.feign.dto.OrderBrief order = new com.mall.common.feign.dto.OrderBrief();
        order.setId(9L);
        order.setOrderNo("20261030123456");
        order.setStatus(30);
        order.setPayAmount(399900L);

        ChatReplyVO vo = orchestrator(order, null).reply(session(), "我的订单到哪了", List.of());
        assertEquals("order", vo.getIntent());
        assertFalse(vo.getContent().isBlank());
        assertTrue(vo.getContent().contains("20261030123456"), "回答必须包含真实订单号：" + vo.getContent());
        assertTrue(vo.getContent().contains("已发货"));
        assertFalse(vo.isFallback());
        assertNotNull(vo.getToolTrace());
        assertTrue(vo.getCards().stream().anyMatch(c -> "order".equals(c.get("type"))));
    }

    @Test
    void FAQ命中时回答原文引用知识库() {
        com.mall.ai.entity.Faq faq = new com.mall.ai.entity.Faq();
        faq.setQuestion("运费怎么算？");
        faq.setAnswer("单笔满 99 元全场包邮。");
        faq.setSynonyms("运费,邮费,包邮,快递费");
        faq.setCategory("物流");
        faq.setStatus(1);

        ChatReplyVO vo = orchestrator(null, faq).reply(session(), "邮费怎么算的", List.of());
        assertEquals("faq", vo.getIntent());
        assertTrue(vo.getContent().contains("99"), "回答必须来自 FAQ：" + vo.getContent());
        assertFalse(vo.isFallback());
    }

    @Test
    void 无证据必须兜底不许编() {
        // 空库 + 订单域查无单
        ChatReplyVO vo = orchestrator(null, null).reply(session(), "满 10 万送手机吗", List.of());
        assertTrue(vo.isFallback());
        assertTrue(vo.isCanTransfer());
        assertTrue(vo.getContent().contains("转人工"));
    }

    @Test
    void 查无此单时兜底且不泄露他人订单() {
        com.mall.common.feign.OrderInnerClient orderClient = mock(com.mall.common.feign.OrderInnerClient.class);
        when(orderClient.orderByNo(anyString(), any(Long.class))).thenReturn(null);
        com.mall.ai.mapper.FaqMapper emptyMapper = mock(com.mall.ai.mapper.FaqMapper.class);
        when(emptyMapper.selectList(any())).thenReturn(List.of());
        FaqRetriever retriever = new FaqRetriever(emptyMapper);
        retriever.invalidate();
        ChatOrchestrator orch = new ChatOrchestrator(new IntentClassifier(),
                new ToolGateway(orderClient, mock(com.mall.common.feign.RefundInnerClient.class)),
                retriever, new Guard(),
                new LlmRouter(List.of((LlmProvider) new com.mall.ai.llm.MockLlmProvider()), "mock"));

        ChatReplyVO vo = orch.reply(session(), "订单号 20261030123456 帮我查下", List.of());
        assertTrue(vo.isFallback());
        assertTrue(vo.isCanTransfer());
    }

}
