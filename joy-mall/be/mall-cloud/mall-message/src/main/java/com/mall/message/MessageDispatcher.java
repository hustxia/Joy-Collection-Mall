package com.mall.message;

import com.mall.entity.MallMessageLog;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息调度（V14 纯逻辑，无 Spring / 无数据库）。
 *
 * <p><b>发送策略（需求 REQ-P1-09）：</b>
 * <pre>
 *   主通道（模板 channel，如 sms）
 *     ├─ 不可用（未配置）      → 不重试，直接降级
 *     ├─ 失败                 → 重试 maxRetries 次（默认 2）→ 仍失败则降级
 *     └─ 成功                 → status=1，落日志，结束
 *   降级通道（site 站内信）
 *     ├─ 成功                 → status=2 + degraded=1，落日志
 *     └─ 失败                 → status=0，错误信息落日志（由运维按 error_msg 重推）
 * </pre>
 *
 * <p>为什么「不可用」不重试：未配置服务商密钥时重试 2 次只是白白拉长请求链路
 * （调用方是支付/发货主链路），配置类故障重试解决不了；这类情况 retryCount 记 0，
 * 日志里 error_msg 明确写「通道不可用」，运维一眼能定位是配置问题还是通道故障。
 */
@Slf4j
public class MessageDispatcher {

    /** 降级目标：站内信 */
    public static final String DEGRADE_CHANNEL = MessageChannel.CODE_SITE;

    private final Map<String, MessageChannel> channels;
    private final MessageLogStore logStore;
    private final Clock clock;
    private final int maxRetries;

    public MessageDispatcher(List<MessageChannel> channels, MessageLogStore logStore, Clock clock, int maxRetries) {
        Map<String, MessageChannel> map = new LinkedHashMap<>();
        for (MessageChannel channel : channels) {
            map.put(channel.code(), channel);
        }
        this.channels = map;
        this.logStore = logStore;
        this.clock = clock;
        this.maxRetries = Math.max(0, maxRetries);
    }

    /**
     * 发送并落日志。
     *
     * @param request       已渲染好的消息
     * @param primaryChannel 主通道码（来自模板配置，空则直接走站内信）
     * @return 落库的日志行（调用方可用于回显状态/排查）
     */
    public MallMessageLog dispatch(MessageRequest request, String primaryChannel) {
        MallMessageLog row = new MallMessageLog();
        row.setSceneCode(request.sceneCode());
        row.setMemberId(request.memberId());
        row.setTitle(request.title());
        row.setContent(request.content());
        row.setBizId(request.bizId());
        row.setRetryCount(0);
        row.setDegraded(0);

        String primaryCode = primaryChannel == null || primaryChannel.isBlank()
                ? DEGRADE_CHANNEL : primaryChannel;
        MessageChannel primary = channels.get(primaryCode);
        int retries = 0;
        String lastError = null;

        if (primary == null) {
            lastError = "通道未注册：" + primaryCode;
        } else if (!primary.available()) {
            lastError = "通道不可用：" + primaryCode;
            log.warn("消息主通道不可用，直接降级站内信：scene={}, channel={}", request.sceneCode(), primaryCode);
        } else {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                MessageChannel.SendResult result = safeSend(primary, request);
                if (result.success()) {
                    row.setChannel(primaryCode);
                    row.setStatus(MallMessageLog.STATUS_SUCCESS);
                    row.setRetryCount(retries);
                    row.setErrorMsg(null);
                    return finish(row);
                }
                lastError = result.error();
                if (attempt < maxRetries) {
                    retries++;
                    log.warn("消息发送失败，重试第 {} 次：scene={}, channel={}, err={}",
                            retries, request.sceneCode(), primaryCode, lastError);
                }
            }
        }

        // 降级：站内信
        MessageChannel site = channels.get(DEGRADE_CHANNEL);
        if (site != null && !DEGRADE_CHANNEL.equals(primaryCode)) {
            MessageChannel.SendResult degraded = safeSend(site, request);
            if (degraded.success()) {
                row.setChannel(DEGRADE_CHANNEL);
                row.setStatus(MallMessageLog.STATUS_DEGRADED);
                row.setRetryCount(retries);
                row.setDegraded(1);
                row.setErrorMsg(lastError);
                log.info("消息已降级为站内信：scene={}, member={}, 主通道失败原因={}",
                        request.sceneCode(), request.memberId(), lastError);
                return finish(row);
            }
            lastError = "主通道[" + lastError + "]，降级通道失败[" + degraded.error() + "]";
        } else if (site != null && DEGRADE_CHANNEL.equals(primaryCode)) {
            // 主通道就是站内信且已失败：不再二次尝试，直接记失败
            lastError = "站内信写入失败：" + lastError;
        }

        row.setChannel(primaryCode);
        row.setStatus(MallMessageLog.STATUS_FAILED);
        row.setRetryCount(retries);
        row.setDegraded(0);
        row.setErrorMsg(truncate(lastError));
        log.error("消息发送最终失败：scene={}, member={}, err={}",
                request.sceneCode(), request.memberId(), lastError);
        return finish(row);
    }

    private MallMessageLog finish(MallMessageLog row) {
        row.setSendTime(LocalDateTime.now(clock));
        try {
            logStore.save(row);
        } catch (Exception e) {
            // 日志落库失败不能反向影响发送结果（发送本身已完成），只打错误日志供补偿
            log.error("消息日志落库失败：scene={}, member={}, {}", row.getSceneCode(), row.getMemberId(), e.getMessage());
        }
        return row;
    }

    /** 通道实现必须自吞异常，这里再兜一层：SDK 抛 RuntimeException 不能炸到支付/发货主链路 */
    private MessageChannel.SendResult safeSend(MessageChannel channel, MessageRequest request) {
        try {
            MessageChannel.SendResult result = channel.send(request);
            return result == null ? MessageChannel.SendResult.fail("通道返回空结果") : result;
        } catch (Exception e) {
            return MessageChannel.SendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 500 ? error : error.substring(0, 500);
    }
}
