package com.mall.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 在线 AI 客服服务（V16，8210）。
 *
 * <p>只写本域 {@code mall_chat_*} / {@code mall_faq} / {@code mall_ai_stat_daily}；
 * 订单、物流、售后数据全部经 Feign 读现有 {@code /inner} 契约，转人工经客服域建单。
 * 换句话说：本服务持有的唯一「新事实」是对话本身。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.ai.mapper"})
@ServletComponentScan
@EnableFeignClients(basePackages = "com.mall.common.feign")
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
