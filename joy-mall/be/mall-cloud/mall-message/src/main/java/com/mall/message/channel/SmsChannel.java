package com.mall.message.channel;

import com.mall.message.MessageChannel;
import com.mall.message.MessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 短信通道（V14，模拟实现）。
 *
 * <p>本期没有短信服务商资质与密钥（离线环境），所以：
 * <ul>
 *   <li>{@code available()} 只看配置开关 {@code app.message.sms.enabled}——
 *       未配置就是「不可用」，调度器直接降级站内信，不做无意义重试；</li>
 *   <li>{@code send()} 只打印模拟发送日志，不真的出网；落库由调度器统一写
 *       {@code mall_message_log}，通道内不重复写；</li>
 *   <li>{@code simulate-fail=true} 时永远返回失败，用来联调「重试 → 降级」链路
 *       （见 {@code MessageFailoverTest} 的生产等价场景）。</li>
 * </ul>
 * 接真实服务商时：替换 {@link #send} 内部实现（或新建 TencentSmsChannel 并注册 Bean），
 * 调度器与业务侧零改动。
 */
@Slf4j
@Component
public class SmsChannel implements MessageChannel {

    private final boolean enabled;
    private final boolean simulateFail;

    public SmsChannel(@Value("${app.message.sms.enabled:false}") boolean enabled,
                      @Value("${app.message.sms.simulate-fail:false}") boolean simulateFail) {
        this.enabled = enabled;
        this.simulateFail = simulateFail;
        if (!enabled) {
            log.info("短信通道未启用（app.message.sms.enabled=false），消息将降级为站内信");
        }
    }

    @Override
    public String code() {
        return MessageChannel.CODE_SMS;
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public SendResult send(MessageRequest request) {
        if (!enabled) {
            return SendResult.fail("短信通道未启用");
        }
        if (simulateFail) {
            return SendResult.fail("模拟失败：服务商返回 500");
        }
        // 模拟发送：真实接入时这里换成服务商 SDK 调用，并按返回码构造 SendResult
        log.info("[模拟短信] member={}, title={}, content={}", request.memberId(),
                request.title(), request.content());
        return SendResult.ok();
    }
}
