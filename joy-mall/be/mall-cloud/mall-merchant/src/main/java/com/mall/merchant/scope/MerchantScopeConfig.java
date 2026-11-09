package com.mall.merchant.scope;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商家数据隔离装配（V11）。
 *
 * <p>以 {@link InnerInterceptor} 的形式交给 {@code mall-common} 的
 * {@code MybatisPlusConfig} 统一挂载——只在本服务（mall-merchant）生效，
 * 且稳定排在分页拦截器之前。
 */
@Configuration
public class MerchantScopeConfig {

    @Bean
    public InnerInterceptor merchantScopeInnerInterceptor() {
        return new MerchantScopeInterceptor();
    }
}
