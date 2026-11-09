package com.mall.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 客服服务（V14，8208）：会员工单提交 / 客服回复 / 首响超时标记 / 分类统计。
 *
 * <p>只写本域 {@code mall_ticket*} 两张表，不依赖其它服务（订单号由前端传入并冗余存储，
 * 不做跨域 Feign 查询——工单不阻塞在订单域可用性上）。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
public class SupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportApplication.class, args);
    }
}
