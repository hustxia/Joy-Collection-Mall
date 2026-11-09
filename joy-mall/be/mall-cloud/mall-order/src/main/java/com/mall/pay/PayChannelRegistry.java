package com.mall.pay;

import com.mall.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道注册表（V12）：按 code 路由到具体 SPI 实现。
 *
 * <p>安全口径：**未配置的渠道不路由**，直接 503 并给出可读原因，
 * 避免前端拿到半成品参数后再失败在支付页上。
 */
@Component
public class PayChannelRegistry {

    private final Map<String, PayChannel> channels = new LinkedHashMap<>();

    public PayChannelRegistry(List<PayChannel> beans) {
        for (PayChannel channel : beans) {
            channels.put(channel.code(), channel);
        }
    }

    /** 取可用渠道：不存在 400，未配置 503 */
    public PayChannel require(String code) {
        String key = code == null || code.isBlank() ? SandboxPayChannel.CODE : code.trim().toLowerCase();
        PayChannel channel = channels.get(key);
        if (channel == null) {
            throw new BusinessException(400, "不支持的支付渠道：" + code);
        }
        if (!channel.configured()) {
            throw new BusinessException(503, "渠道 " + key + " 未配置商户资质，请使用 sandbox 渠道");
        }
        return channel;
    }

    /** 渠道清单（后台/前端展示可选渠道用） */
    public Map<String, Boolean> availability() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        channels.forEach((code, channel) -> map.put(code, channel.configured()));
        return map;
    }
}
