package com.mall.common.feign;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 纯读客户端的重试配置：最多 3 次尝试（首次 + 2 次重试），间隔 100ms 起、上限 500ms。
 *
 * <p>只允许挂在<b>全部端点都是 GET</b> 的客户端上（当前是 {@link RefundInnerClient}）。
 * 挂到带写端点的客户端上等于给扣库存/退款开了自动重试，会放大重复扣减风险。
 *
 * <p><b>必须 {@code @Primary}</b>：Feign 每个客户端跑在独立子上下文里，子上下文以应用上下文为父，
 * 而父上下文里的 {@link FeignConfig#neverRetry()} 同样是 {@code Retryer} 类型。按类型注入时
 * Spring 会把父上下文的候选也一起算进来，{@code DefaultFeignBuilderConfiguration#feignBuilder}
 * 就报「required a single bean, but 2 were found」。标了 {@code @Primary} 后本子上下文内的
 * retryableReadRetryer 优先胜出，且<b>只影响该客户端</b>，不改变其他客户端的 NEVER_RETRY 语义。
 */
public class RetryableReadFeignConfig {

    @Bean
    @Primary
    public Retryer retryableReadRetryer() {
        return new Retryer.Default(100L, 500L, 3);
    }
}
