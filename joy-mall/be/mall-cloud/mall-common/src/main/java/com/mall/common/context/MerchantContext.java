package com.mall.common.context;

/**
 * 商家登录上下文（ThreadLocal）。
 *
 * <p>与 {@link UserContext} 并列而非合并：平台用户 ID / 商家用户 ID 是两套 ID 空间，
 * 合并会让审计字段（create_by）串味，也给了「拿平台 ID 冒充商家」的口子。
 *
 * <p>由鉴权过滤器在 {@code Merchant-Token} 校验通过后写入，业务代码只读；
 * 过滤链结束必须 {@link #clear()}，否则线程池复用会把商家身份带给下一个请求。
 */
public class MerchantContext {

    private static final ThreadLocal<Long> MERCHANT_ID = new ThreadLocal<>();

    private static final ThreadLocal<Long> MERCHANT_USER_ID = new ThreadLocal<>();

    private static final ThreadLocal<String> MERCHANT_USERNAME = new ThreadLocal<>();

    private static final ThreadLocal<String> MERCHANT_ROLE = new ThreadLocal<>();

    public static void set(Long merchantId, Long merchantUserId, String username, String role) {
        MERCHANT_ID.set(merchantId);
        MERCHANT_USER_ID.set(merchantUserId);
        MERCHANT_USERNAME.set(username);
        MERCHANT_ROLE.set(role);
    }

    /** 当前商家 ID；非商家请求返回 null（调用方必须自行判空） */
    public static Long get() {
        return MERCHANT_ID.get();
    }

    public static Long getMerchantId() {
        return MERCHANT_ID.get();
    }

    public static Long getMerchantUserId() {
        return MERCHANT_USER_ID.get();
    }

    public static String getUsername() {
        return MERCHANT_USERNAME.get();
    }

    public static String getRole() {
        return MERCHANT_ROLE.get();
    }

    public static void clear() {
        MERCHANT_ID.remove();
        MERCHANT_USER_ID.remove();
        MERCHANT_USERNAME.remove();
        MERCHANT_ROLE.remove();
    }
}
