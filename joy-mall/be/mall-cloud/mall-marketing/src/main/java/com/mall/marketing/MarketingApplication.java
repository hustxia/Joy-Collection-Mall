package com.mall.marketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 营销服务：优惠券 / 优惠券领取记录 + C 端领券 + V9 分销与佣金。
 *
 * <p>V9 起分销/佣金要跨域读写会员余额（{@code MemberInnerClient}），
 * 必须开启 Feign 客户端扫描，否则 MallDistributorServiceImpl 直接启动失败。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
@ServletComponentScan
@EnableFeignClients(basePackages = "com.mall.common.feign")
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
