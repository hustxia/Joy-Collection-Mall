package com.mall.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 公共配置：服务间调用透传当前请求的 Authorization 头，
 * 保证内部链路在需要身份时延续会员/后台会话（/inner 端点本身免鉴权，透传无害）。
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignConfig {

    /**
     * 全局重试策略：<b>不重试</b>。
     *
     * <p>Feign 的重试是「客户端级」而非「方法级」：一旦给带写端点的客户端开了重试，
     * 扣库存、锁券、退款也会跟着重试，一次网络抖动就可能重复扣减。
     * 所以默认一律禁止；确有需要的纯读客户端用
     * {@link RetryableReadFeignConfig} 在自己的 {@code configuration} 里覆盖。
     */
    @Bean
    public Retryer neverRetry() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor authorizationForwardingInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                try {
                    // /inner/** 是内部端点，本身免鉴权；不透传用户令牌，
                    // 否则被调方会把会员令牌当成越权访问非 /shop 接口而 403。
                    String path = template.path();
                    if (path != null && path.startsWith("/inner")) {
                        return;
                    }
                    ServletRequestAttributes attrs =
                            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs == null) {
                        return;
                    }
                    HttpServletRequest request = attrs.getRequest();
                    String auth = request.getHeader("Authorization");
                    if (auth != null && !auth.isBlank()) {
                        template.header("Authorization", auth);
                    }
                } catch (Exception ignored) {
                    // 非 Web 上下文（如测试）忽略
                }
            }
        };
    }
}
