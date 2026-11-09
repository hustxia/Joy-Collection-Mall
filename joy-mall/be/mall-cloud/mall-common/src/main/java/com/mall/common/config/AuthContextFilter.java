package com.mall.common.config;

import com.mall.common.auth.PermissionChecker;
import com.mall.common.auth.TokenStore;
import com.mall.common.context.MerchantContext;
import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 登录上下文填充 + 两级鉴权拦截。
 *
 * <p>V6.4 权限闭环：第一级登录校验（token 有效性，401）；
 * 第二级接口权限点校验（{@link PermissionChecker}，无权 403）——
 * 结束「前端 v-hasPermi 藏按钮、后端裸奔」的历史。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthContextFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private PermissionChecker permissionChecker;

    /** 无需 token 即可访问的路径前缀 */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/captcha",
            "/health",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/error",
            // V6.6 文件能力包：图片直链不走鉴权（img 标签不带 token）
            "/uploads",
            // 打包内置静态商品图（/img/goods/**），img 标签直链同 /uploads
            "/img",
            // V4 收尾 P1-2：缩略图端点（同样免鉴权，img/列表直链引用）
            "/thumb",
            // 微服务内部调用端点（Feign 服务间调用，不对外暴露，免鉴权）
            "/inner",
            // V11 商家登录：账号密码在请求体里，天然免令牌（令牌校验在登录实现内部）
            "/merchant-admin/auth/login"
    );

    /**
     * C 端匿名白名单（V3 收窄）：方法+路径前缀二元组，仅浏览只读与注册登录放行。
     * 取代旧 "/shop" 整前缀放行——/shop 下的写接口（购物车/下单/支付）必须持会员 token。
     */
    private static final List<String[]> SHOP_ANON_LIST = List.of(
            new String[]{"POST", "/shop/auth/register"},
            new String[]{"POST", "/shop/auth/login"},
            new String[]{"GET", "/shop/banners"},
            new String[]{"GET", "/shop/categories"},
            new String[]{"GET", "/shop/goods"},
            // V7：商品评价列表匿名可读（详情页直接拉取），提交评价仍需会员 token
            new String[]{"GET", "/shop/review/goods"},
            // V10：店铺浏览匿名可读（店铺页/店铺信息）
            new String[]{"GET", "/shop/merchant"},
            // V14：搜索增强（REQ-P1-04）与 V8/V9 活动列表匿名可读；
            // 抢购/开团/抽奖/分销等写接口仍必须持有会员 token
            new String[]{"GET", "/shop/search"},
            new String[]{"GET", "/shop/seckill"},
            new String[]{"GET", "/shop/group"},
            new String[]{"GET", "/shop/lottery"},
            // V15：促销活动标签匿名可读（商品卡/详情页未登录也要能看到活动角标），只读
            new String[]{"GET", "/shop/promotion/available"},
            // V12：支付/退款回调由渠道服务器发起，不带会员 token；安全边界在渠道验签
            new String[]{"POST", "/shop/pay/notify"}
    );

    /** 会员 token 的 username 前缀（与后台 sys_user 会话区分） */
    public static final String MEMBER_PREFIX = "m:";

    /** 商家 token 的 username 前缀：mt:{merchantUserId}:{merchantId}，与会员/平台三方互不通用 */
    public static final String MERCHANT_PREFIX = "mt:";

    /** 商家令牌请求头：不与 Authorization 共用，避免浏览器/代理把两套令牌混在一起 */
    public static final String MERCHANT_TOKEN_HEADER = "Merchant-Token";

    /** 商家后台路径前缀：商家令牌只允许打这里，平台/会员令牌一律 403 */
    private static final String MERCHANT_ADMIN_PREFIX = "/merchant-admin";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS 预检不带 Authorization，必须放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        boolean hasToken = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX);
        String path = stripContextPath(request);

        // V11 商家分支（最先判定）：Merchant-Token 是商家后台的唯一入口，
        // 只认这个头、只放行 /merchant-admin/**；平台与会员令牌在下面统一 403/401。
        String merchantToken = request.getHeader(MERCHANT_TOKEN_HEADER);
        if (StringUtils.hasText(merchantToken)) {
            if (!isMerchantAdminPath(path)) {
                writeForbidden(response, "商家身份仅可访问商家后台接口");
                return;
            }
            handleMerchant(merchantToken, response, request, filterChain);
            return;
        }
        // 平台/会员令牌打商家后台（登录端点除外）→ 403：两侧令牌不通用
        if (isMerchantAdminPath(path) && hasToken && !isWhiteListed(request)) {
            writeForbidden(response, "平台账号不可访问商家后台，请使用商家账号登录");
            return;
        }

        if (hasToken) {
            String token = authorization.substring(BEARER_PREFIX.length());
            // 第一级：登录校验（过期/登出即 401）
            String username = tokenStore.usernameOf(token);
            if (username == null) {
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }
            // 商家令牌塞进 Authorization 也进不来：前缀不认就直接拒绝，防止绕过 Merchant-Token 分流
            if (username.startsWith(MERCHANT_PREFIX)) {
                writeForbidden(response, "商家身份仅可访问商家后台接口");
                return;
            }
            // V3 会员分支：m: 前缀会话跳过 sys 权限体系，仅允许 /shop/**；
            // 入参 memberId 一律忽略，会员身份只认 UserContext
            if (username.startsWith(MEMBER_PREFIX)) {
                if (!path.startsWith("/shop/")) {
                    writeForbidden(response, "会员身份仅可访问商城前台接口");
                    return;
                }
                Long memberId = parseMemberId(username);
                if (memberId == null) {
                    writeUnauthorized(response, "会员会话异常，请重新登录");
                    return;
                }
                UserContext.setUserId(memberId);
                UserContext.setUsername(username);
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    UserContext.clear();
                }
                return;
            }
            // 后台 token 访问非白名单 /shop/** → 401：职责隔离，C 端写接口只认会员身份
            if (path.startsWith("/shop/") && !isShopAnonymous(request, path)) {
                writeUnauthorized(response, "该接口需会员登录，请使用会员账号");
                return;
            }
            // 第二级：接口权限点校验（V6.4 fail-closed：映射缺失读放行写拒绝）
            if (!permissionChecker.check(request, username)) {
                writeForbidden(response, "无该操作权限");
                return;
            }
            UserContext.setUsername(username);
            // A-01：填充 userId，审计自动填充/操作日志/流程操作人都依赖它
            Long userId = permissionChecker.userIdOf(username);
            if (userId != null) {
                UserContext.setUserId(userId);
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
            return;
        }

        if (isWhiteListed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        writeUnauthorized(response, "未登录或缺少访问令牌");
    }

    private boolean isWhiteListed(HttpServletRequest request) {
        String path = stripContextPath(request);
        for (String prefix : WHITE_LIST) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return isShopAnonymous(request, path);
    }

    /** 商家后台路径判定：/merchant-admin 与 /merchant-admin/** 都算 */
    private boolean isMerchantAdminPath(String path) {
        return path.equals(MERCHANT_ADMIN_PREFIX) || path.startsWith(MERCHANT_ADMIN_PREFIX + "/");
    }

    /**
     * 商家令牌链路：校验会话 → 解析 merchantId → 写入上下文 → 放行。
     *
     * <p>商家令牌不参与 sys 权限点校验：商家侧权限是「本店数据范围」，由
     * {@code @MerchantScoped} 拦截器保证，混进平台权限体系反而会出现
     * 「有 perms 却能跨店」的漏洞。
     */
    private void handleMerchant(String token, HttpServletResponse response, HttpServletRequest request,
                                FilterChain filterChain) throws ServletException, IOException {
        String username = tokenStore.usernameOf(token);
        if (username == null || !username.startsWith(MERCHANT_PREFIX)) {
            writeUnauthorized(response, "商家登录状态已失效，请重新登录");
            return;
        }
        long[] ids = parseMerchantIdentity(username);
        if (ids == null) {
            writeUnauthorized(response, "商家会话异常，请重新登录");
            return;
        }
        MerchantContext.set(ids[1], ids[0], username, null);
        UserContext.setUserId(ids[0]);
        UserContext.setUsername(username);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            MerchantContext.clear();
        }
    }

    /** mt:{merchantUserId}:{merchantId} → [merchantUserId, merchantId] */
    private long[] parseMerchantIdentity(String username) {
        String[] parts = username.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(parts[1]), Long.parseLong(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** C 端匿名放行：方法+前缀双匹配，写接口永不进入此名单 */
    private boolean isShopAnonymous(HttpServletRequest request, String path) {
        String method = request.getMethod();
        for (String[] entry : SHOP_ANON_LIST) {
            if (entry[0].equals(method) && (path.equals(entry[1]) || path.startsWith(entry[1] + "/"))) {
                return true;
            }
        }
        return false;
    }

    private String stripContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private Long parseMemberId(String username) {
        try {
            return Long.parseLong(username.substring(MEMBER_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED.getCode(), msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.fail(ResultCode.FORBIDDEN.getCode(), msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
