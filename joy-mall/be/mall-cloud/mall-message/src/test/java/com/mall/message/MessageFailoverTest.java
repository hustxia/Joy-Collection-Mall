package com.mall.message;

import com.mall.entity.MallMessageLog;
import com.mall.entity.MallMessageTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消息失败降级纯单测（V14，REQ-P1-09）。
 *
 * <p>不启动 Spring、不连库：{@link MessageDispatcher} 只依赖通道列表与日志存储端口。
 * 用可编排的假通道把需求验收口径锁死：
 * 「短信通道故障时站内信仍写入且日志标记降级」「重试次数有上限」。
 * 真实短信/站内信落库由手工冒烟与服务级集成测试覆盖。
 */
class MessageFailoverTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    @DisplayName("短信通道不可用：不重试、直接降级站内信，日志 degraded=1 / status=2")
    void smsUnavailableDegradesToSite() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, false, false);
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, true);
        InMemoryLogStore store = new InMemoryLogStore();
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms, site), store, CLOCK, 2);

        MallMessageLog row = dispatcher.dispatch(request(), MessageChannel.CODE_SMS);

        assertEquals(0, sms.attempts.get(), "通道不可用时不应做无意义重试");
        assertEquals(1, site.attempts.get(), "降级站内信必须尝试一次");
        assertEquals(MallMessageLog.STATUS_DEGRADED, row.getStatus());
        assertEquals(1, row.getDegraded(), "降级必须打标记");
        assertEquals(MessageChannel.CODE_SITE, row.getChannel());
        assertEquals(0, row.getRetryCount());
        assertTrue(row.getErrorMsg().contains("不可用"));
        assertNotNull(row.getSendTime());
        assertEquals(1, store.saved.size(), "每次发送都要落一条日志");
        assertEquals(1, store.saved.get(0).getDegraded());
    }

    @Test
    @DisplayName("短信通道返回失败：重试 2 次（共尝试 3 次）后降级，retryCount=2")
    void smsFailureRetriesThenDegrades() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, true, false);
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, true);
        InMemoryLogStore store = new InMemoryLogStore();
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms, site), store, CLOCK, 2);

        MallMessageLog row = dispatcher.dispatch(request(), MessageChannel.CODE_SMS);

        assertEquals(3, sms.attempts.get(), "首次 + 2 次重试 = 3 次尝试");
        assertEquals(2, row.getRetryCount(), "retryCount 只记重试次数，不含首次");
        assertEquals(MallMessageLog.STATUS_DEGRADED, row.getStatus());
        assertEquals(1, row.getDegraded());
        assertEquals(MessageChannel.CODE_SITE, row.getChannel());
        assertTrue(row.getErrorMsg().contains("服务商 500"), "要保留最后一次失败原因，便于排障");
    }

    @Test
    @DisplayName("重试次数上限可配：maxRetries=0 时只尝试 1 次就降级")
    void retryLimitIsConfigurable() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, true, false);
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, true);
        InMemoryLogStore store = new InMemoryLogStore();
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms, site), store, CLOCK, 0);

        MallMessageLog row = dispatcher.dispatch(request(), MessageChannel.CODE_SMS);

        assertEquals(1, sms.attempts.get());
        assertEquals(0, row.getRetryCount());
        assertEquals(MallMessageLog.STATUS_DEGRADED, row.getStatus());
    }

    @Test
    @DisplayName("主通道与降级通道都失败：status=0、degraded=0、error_msg 含两侧原因")
    void bothChannelsFail() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, true, false);
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, false);
        InMemoryLogStore store = new InMemoryLogStore();
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms, site), store, CLOCK, 2);

        MallMessageLog row = dispatcher.dispatch(request(), MessageChannel.CODE_SMS);

        assertEquals(MallMessageLog.STATUS_FAILED, row.getStatus());
        assertEquals(0, row.getDegraded());
        assertEquals(MessageChannel.CODE_SMS, row.getChannel());
        assertEquals(2, row.getRetryCount());
        assertTrue(row.getErrorMsg().contains("降级通道失败"));
        assertEquals(1, store.saved.size());
    }

    @Test
    @DisplayName("短信正常：status=1、degraded=0、不写站内信")
    void smsSuccessNoDegrade() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, true, true);
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, true);
        InMemoryLogStore store = new InMemoryLogStore();
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms, site), store, CLOCK, 2);

        MallMessageLog row = dispatcher.dispatch(request(), MessageChannel.CODE_SMS);

        assertEquals(MallMessageLog.STATUS_SUCCESS, row.getStatus());
        assertEquals(0, row.getDegraded());
        assertEquals(0, row.getRetryCount());
        assertEquals(0, site.attempts.get(), "主通道成功就不该再发站内信（避免用户收到双份）");
        assertNull(row.getErrorMsg());
    }

    @Test
    @DisplayName("模板渲染：金额分转元、缺变量保留占位符、null 值不替换")
    void renderTemplate() {
        String content = MessageTemplateRenderer.render(
                "订单 ${orderNo} 支付成功，金额 ${amount} 元，${extra}",
                Map.of("orderNo", "SO20261101001", "amount", 499900L));
        assertEquals("订单 SO20261101001 支付成功，金额 4999.00 元，${extra}", content);

        String nullValue = MessageTemplateRenderer.render("金额 ${amount}", Map.of("amount", ""));
        assertEquals("金额 ", nullValue);
        assertEquals("", MessageTemplateRenderer.render(null, Map.of()));
        assertEquals("无变量", MessageTemplateRenderer.render("无变量", null));
    }

    @Test
    @DisplayName("通道抛异常：调度器兜住，不让异常穿透到支付/发货主链路")
    void channelExceptionIsContained() {
        MessageChannel boom = new MessageChannel() {
            @Override
            public String code() {
                return MessageChannel.CODE_SMS;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public SendResult send(MessageRequest request) {
                throw new IllegalStateException("SDK 崩了");
            }
        };
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, true);
        InMemoryLogStore store = new InMemoryLogStore();
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(boom, site), store, CLOCK, 2);

        MallMessageLog row = assertDoesNotThrow(() -> dispatcher.dispatch(request(), MessageChannel.CODE_SMS));
        assertEquals(MallMessageLog.STATUS_DEGRADED, row.getStatus());
        assertTrue(row.getErrorMsg().contains("SDK 崩了"));
    }

    @Test
    @DisplayName("日志落库失败：不影响发送结果，不抛异常")
    void logStoreFailureDoesNotThrow() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, true, true);
        MessageLogStore brokenStore = log -> {
            throw new IllegalStateException("DB down");
        };
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms), brokenStore, CLOCK, 2);

        MallMessageLog row = assertDoesNotThrow(() -> dispatcher.dispatch(request(), MessageChannel.CODE_SMS));
        assertEquals(MallMessageLog.STATUS_SUCCESS, row.getStatus());
    }

    @Test
    @DisplayName("模板通道为空：直接走站内信（不把 null 当主通道去打短信）")
    void blankChannelGoesToSite() {
        FakeChannel sms = new FakeChannel(MessageChannel.CODE_SMS, true, true);
        FakeChannel site = new FakeChannel(MessageChannel.CODE_SITE, true, true);
        MessageDispatcher dispatcher = new MessageDispatcher(List.of(sms, site), new InMemoryLogStore(), CLOCK, 2);

        MallMessageLog row = dispatcher.dispatch(request(), null);
        assertEquals(0, sms.attempts.get());
        assertEquals(1, site.attempts.get());
        assertEquals(MessageChannel.CODE_SITE, row.getChannel());
        assertFalse(Integer.valueOf(1).equals(row.getDegraded()), "本来就走站内信，不算降级");
    }

    // ===== 测试替身 =====

    private static MessageRequest request() {
        return new MessageRequest(MallMessageTemplate.SCENE_PAY_SUCCESS, 3001L,
                "支付成功通知", "您的订单 SO20261101001 已支付成功，金额 4999.00 元", "SO20261101001");
    }

    /** 可编排假通道：available/success 两开关 + 尝试次数计数 */
    private static final class FakeChannel implements MessageChannel {
        private final String code;
        private final boolean available;
        private final boolean success;
        private final AtomicInteger attempts = new AtomicInteger();

        FakeChannel(String code, boolean available, boolean success) {
            this.code = code;
            this.available = available;
            this.success = success;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public SendResult send(MessageRequest request) {
            attempts.incrementAndGet();
            return success ? SendResult.ok() : SendResult.fail("服务商 500");
        }
    }

    /** 只收集、不落库 */
    private static final class InMemoryLogStore implements MessageLogStore {
        private final List<MallMessageLog> saved = new ArrayList<>();

        @Override
        public void save(MallMessageLog log) {
            saved.add(log);
        }
    }
}
