package com.mall.goods;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品服务：商品 / SKU / 品牌 / 分类 / Banner + C 端商品。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
@ServletComponentScan
@EnableFeignClients(basePackages = "com.mall.common.feign")
public class GoodsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsApplication.class, args);
    }
}
