package com.mall.pay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沙箱支付渠道（V12）：本地联调与自动化测试用，行为与真实渠道同构——
 * 先下单拿参数，再由「渠道」回调通知结果，回调带签名。
 *
 * <p>签名口径（前后端与测试脚本同源）：
 * <pre>sign = md5(payNo + channelTradeNo + amount + status + secret)，小写十六进制</pre>
 * 验签失败一律 400，且不落任何状态——这是防伪造回调的唯一关口，不能为了联调放行。
 */
@Slf4j
@Component
public class SandboxPayChannel implements PayChannel {

    public static final String CODE = "sandbox";

    private static final ObjectMapper OM = new ObjectMapper();

    private final String secret;

    public SandboxPayChannel(@Value("${mall.pay.sandbox.secret:yueji-sandbox-secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public Map<String, Object> prepay(MallPayment payment) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("channel", CODE);
        params.put("payNo", payment.getPayNo());
        params.put("tradeType", payment.getTradeType());
        params.put("amount", payment.getAmount());
        // 前端把该链接渲染成二维码即可「扫码支付」；沙箱下等同于直接调 notify
        params.put("qrCode", "sandbox://pay?payNo=" + payment.getPayNo());
        params.put("notifyUrl", "/shop/pay/notify/" + CODE);
        params.put("tip", "沙箱渠道：调用 /shop/pay/notify/sandbox 模拟支付成功回调");
        return params;
    }

    @Override
    public PayNotify verifyNotify(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            throw new BusinessException(400, "回调报文为空");
        }
        JsonNode node;
        try {
            node = OM.readTree(rawBody);
        } catch (Exception e) {
            throw new BusinessException(400, "回调报文格式错误");
        }
        String payNo = text(node, "payNo");
        String channelTradeNo = text(node, "channelTradeNo");
        long amount = node.path("amount").asLong(-1L);
        int status = node.path("status").asInt(0);
        String sign = text(node, "sign");
        if (!StringUtils.hasText(payNo) || !StringUtils.hasText(channelTradeNo) || amount < 0) {
            throw new BusinessException(400, "回调报文缺少必填字段");
        }
        String expect = sign(payNo, channelTradeNo, amount, status);
        if (!expect.equalsIgnoreCase(sign)) {
            log.warn("沙箱回调验签失败：payNo={}, 期望 {}，实收 {}", payNo, expect, sign);
            throw new BusinessException(400, "回调验签失败");
        }
        PayNotify notify = new PayNotify();
        notify.setPayNo(payNo);
        notify.setChannelTradeNo(channelTradeNo);
        notify.setAmount(amount);
        notify.setStatus(status);
        notify.setRaw(rawBody);
        return notify;
    }

    /** 签名（测试脚本与 C 端联调工具复用同一口径） */
    public String sign(String payNo, String channelTradeNo, long amount, int status) {
        String raw = payNo + channelTradeNo + amount + status + secret;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
