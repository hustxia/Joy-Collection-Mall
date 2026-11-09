package com.mall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务：订单 / 订单项 / 订单日志 + 支付 + 关单回补。
 *
 * <p>跨域读取（商品/会员/营销）经 Feign 调对应服务；
 * 定时关单 {@code OrderCloseScheduler} 需要 {@link EnableScheduling}。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
@ServletComponentScan
@EnableScheduling
@EnableFeignClients(basePackages = "com.mall.common.feign")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
