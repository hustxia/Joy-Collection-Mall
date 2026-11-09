package com.mall.merchant.support;

import com.mall.common.exception.BusinessException;

import java.util.Map;

/**
 * 内部端点返回值拆包（V11）。
 *
 * <p>内部 RPC 成功时返回裸载荷，失败时被 {@code GlobalExceptionHandler} 包成
 * {@code {code,msg,data}} 但 HTTP 仍是 200——商家域必须把这种「200 里的失败」
 * 还原成业务异常，否则前端会拿到一个 code=403 却自称成功的响应。
 */
public final class MerchantResults {

    private MerchantResults() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> unwrap(Map<String, Object> body) {
        if (body != null && body.containsKey("code") && body.containsKey("msg")) {
            Object code = body.get("code");
            if (code instanceof Number number && number.intValue() != 200) {
                throw new BusinessException(number.intValue(), String.valueOf(body.get("msg")));
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of();
        }
        return body == null ? Map.of() : body;
    }
}
