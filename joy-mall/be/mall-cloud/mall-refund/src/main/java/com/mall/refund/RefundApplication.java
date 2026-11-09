package com.mall.refund;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 售后服务：退款 / 售后单 + C 端申请。
 * <p>订单/明细/日志经订单域 Feign、会员昵称经会员域 Feign、退货回补库存经商品域 Feign。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
@ServletComponentScan
@EnableFeignClients(basePackages = "com.mall.common.feign")
public class RefundApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefundApplication.class, args);
    }
}
