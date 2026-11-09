package com.mall.gateway;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关访问日志（V6 O-01/O-03）：每个请求一行，带 traceId、状态码与耗时。
 *
 * <p>做成 {@link WebFilter}（而不是 Gateway 的 GlobalFilter）：观测 Filter 注册在
 * {@code HIGHEST_PRECEDENCE + 1}，本类排在它之后（+2），因此进入时 traceId 已在
 * 当前线程的 MDC 里；且它包住整个网关分发链路，鉴权/限流直接拒绝的请求同样会被记录。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
public class GatewayAccessLogFilter implements WebFilter {

    private final Tracer tracer;

    public GatewayAccessLogFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        String traceId = currentTraceId();
        return chain.filter(exchange)
                .doFinally(signal -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    log.info("traceId={} {} {} -> {} {}ms", traceId, method, path,
                            status == null ? "-" : status.value(), System.currentTimeMillis() - start);
                });
    }

    private String currentTraceId() {
        String fromMdc = MDC.get("traceId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        var span = tracer.currentSpan();
        return span == null ? "-" : span.context().traceId();
    }
}
