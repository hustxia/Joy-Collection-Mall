package com.mall.member;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 会员服务：会员 / 地址 / 购物车 / 足迹 + 认证。
 * <p>购物车列表需读商品/SKU 快照，经商品域 Feign 内部接口获取。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
@ServletComponentScan
@EnableFeignClients(basePackages = "com.mall.common.feign")
public class MemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberApplication.class, args);
    }
}
