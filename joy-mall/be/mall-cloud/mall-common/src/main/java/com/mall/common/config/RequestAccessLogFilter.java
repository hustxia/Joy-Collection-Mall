package com.mall.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 访问日志（V6 O-01/O-03）：每个请求一行，带 traceId、状态码与耗时。
 *
 * <p>顺序排在观测 Filter（HIGHEST_PRECEDENCE + 1）之内、鉴权（+10）之外，
 * 这样 traceId 已由观测链路写入 MDC，且 401/403 被鉴权拦下的请求同样有日志。
 * {@code /actuator}、{@code /health} 不记，避免 compose 探活把日志刷满。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Slf4j
public class RequestAccessLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();
            if (!path.startsWith("/actuator") && !path.startsWith("/health")) {
                log.info("traceId={} {} {} -> {} {}ms", MDC.get("traceId"), request.getMethod(), path,
                        response.getStatus(), System.currentTimeMillis() - start);
            }
        }
    }
}
