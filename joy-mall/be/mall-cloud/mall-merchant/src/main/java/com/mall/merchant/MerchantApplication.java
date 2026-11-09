package com.mall.merchant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商家服务（V10）：商家入驻/审核、店铺、货款结算与商家提现。
 *
 * <p>被 order（订单完成结算）、refund（退款冲减）、goods（店铺信息展示）通过 /inner 契约调用；
 * 自身只写本域 mall_merchant* 表，不反向依赖其他业务域（无需 Feign 客户端）。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
public class MerchantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
