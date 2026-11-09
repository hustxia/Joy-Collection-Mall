package com.mall.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关统一鉴权（V6 S-03）。
 *
 * <p>职责：
 * <ol>
 *   <li>{@code /inner/**} 一律 403——内部端点只允许服务间直连，绝不对外暴露</li>
 *   <li>白名单（方法+路径前缀）匿名放行，其余必须带有效令牌</li>
 *   <li>校验通过注入 {@code X-User-Id} / {@code X-User-Type} / {@code X-Username}，
 *       服务侧 AuthContextFilter 仍保留双校验（网关开关关闭或库抖动时不降级安全性）</li>
 * </ol>
 *
 * <p>库异常时放行给下游校验（fail-open），因为服务侧仍有完整鉴权，比全站 401 更稳。
 */
@Component
@Slf4j
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";

    /** V11 商家后台前缀：令牌为 Merchant-Token，网关只透传不做平台令牌校验 */
    private static final String MERCHANT_ADMIN_PREFIX = "/merchant-admin";

    @Autowired
    private GatewayTokenResolver tokenResolver;

    @Value("${gateway.auth.enabled:true}")
    private boolean enabled;

    /** 追加白名单（逗号分隔，格式 "METHOD /prefix"，或只写 "/prefix" 表示不限方法） */
    @Value("${gateway.auth.extra-whitelist:}")
    private String extraWhitelist = "";

    private volatile List<Rule> rules;

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        // CORS 预检不带令牌
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        // 内部端点永不对外
        if (path.startsWith("/inner/") || path.equals("/inner")) {
            return reject(exchange, HttpStatus.FORBIDDEN, 403, "内部接口不对外暴露");
        }
        if (!enabled) {
            return chain.filter(exchange);
        }

        String method = request.getMethod().name();
        if (isWhitelisted(method, path)) {
            return chain.filter(exchange);
        }

        // 商家后台（V11）：令牌走 Merchant-Token 而非 Authorization Bearer，
        // 网关不解析商家令牌，只做透传，鉴权交给 mall-common 的 AuthContextFilter。
        // 这里若按平台令牌拦截，商家登录本身就是无令牌请求，会被一刀切 401。
        if (isMerchantAdminPath(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或缺少访问令牌");
        }
        String token = authorization.substring(BEARER.length());

        return Mono.fromCallable(() -> tokenResolver.resolve(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(identity -> chain.filter(exchange.mutate()
                        .request(withIdentity(request, identity))
                        .build()))
                // resolver 返回 null 时 Mono 为空，必须兜底 401
                .switchIfEmpty(Mono.defer(() ->
                        reject(exchange, HttpStatus.UNAUTHORIZED, 401, "登录状态已失效，请重新登录")))
                .onErrorResume(ex -> {
                    // 库不可用：放行给下游双校验，避免网关成为全站单点
                    log.warn("网关令牌校验异常，放行交由下游校验：{}", ex.getMessage());
                    return chain.filter(exchange);
                });
    }

    private ServerHttpRequest withIdentity(ServerHttpRequest request, GatewayTokenResolver.Identity identity) {
        return request.mutate()
                .header("X-User-Id", identity.userId() == null ? "" : identity.userId())
                .header("X-User-Type", identity.userType())
                .header("X-Username", identity.username())
                .build();
    }

    /** 商家后台路径：/merchant-admin 及其子路径 */
    private boolean isMerchantAdminPath(String path) {
        return path.equals(MERCHANT_ADMIN_PREFIX) || path.startsWith(MERCHANT_ADMIN_PREFIX + "/");
    }

    private boolean isWhitelisted(String method, String path) {
        for (Rule rule : rules()) {
            if (!"*".equals(rule.method()) && !rule.method().equals(method)) {
                continue;
            }
            if (path.equals(rule.path()) || path.startsWith(rule.path() + "/")) {
                return true;
            }
        }
        return false;
    }

    private synchronized List<Rule> rules() {
        if (rules != null) {
            return rules;
        }
        List<String> raw = new ArrayList<>(DEFAULT_WHITELIST);
        if (extraWhitelist != null && !extraWhitelist.isBlank()) {
            for (String piece : extraWhitelist.split(",")) {
                if (!piece.isBlank()) {
                    raw.add(piece.trim());
                }
            }
        }
        List<Rule> parsed = new ArrayList<>();
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int idx = trimmed.indexOf(' ');
            if (idx < 0) {
                // 只写路径时默认不限方法
                parsed.add(new Rule("*", trimmed));
            } else {
                parsed.add(new Rule(trimmed.substring(0, idx).toUpperCase(), trimmed.substring(idx + 1).trim()));
            }
        }
        rules = parsed;
        return rules;
    }

    /** 默认白名单：与 11 条路由逐条对照，只放行匿名可读与登录入口 */
    private static final List<String> DEFAULT_WHITELIST = List.of(
            "POST /auth/login",
            "GET /auth/captcha",
            "POST /auth/logout",
            "POST /shop/auth/register",
            "POST /shop/auth/login",
            "GET /shop/banners",
            "GET /shop/categories",
            "GET /shop/goods",
            "GET /shop/merchant",
            // V14：搜索与 V8/V9 活动列表匿名可读（下单/抢购等写接口仍需会员 token）
            "GET /shop/search",
            "GET /shop/seckill",
            "GET /shop/group",
            "GET /shop/lottery",
            // V15：商品卡/详情未登录也要能看活动角标与活动价（试算与下单仍需 token）
            "GET /shop/promotion/available",
            "GET /shop/promotion/goods",
            // V12：支付/退款回调由渠道服务器发起，不带会员 token；安全边界在渠道验签
            "POST /shop/pay/notify",
            "* /health",
            "* /actuator",
            "* /img",
            "* /uploads",
            "* /thumb",
            "* /file",
            "* /v3/api-docs",
            "* /swagger-ui"
    );

    private record Rule(String method, String path) {
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
