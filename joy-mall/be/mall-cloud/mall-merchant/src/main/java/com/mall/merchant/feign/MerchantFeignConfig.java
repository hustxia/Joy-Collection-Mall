package com.mall.merchant.feign;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 商家域 Feign 装配（V11）。
 *
 * <p>商家域历史上不调用其他服务（V10 只被别人调），所以启动类没开 Feign；
 * 这里用独立配置类只开 {@code com.mall.merchant.feign}，不动启动类、也不引入
 * {@code com.mall.common.feign} 里那批「商家域不该反向依赖」的客户端。
 */
@Configuration
@EnableFeignClients(basePackages = "com.mall.merchant.feign")
public class MerchantFeignConfig {
}
