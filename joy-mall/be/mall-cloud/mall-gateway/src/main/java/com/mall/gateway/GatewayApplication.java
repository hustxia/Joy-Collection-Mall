package com.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一网关：Spring Cloud Gateway，按路径前缀路由到各业务服务。
 * <p>不引入 mall-common（无 Servlet / DataSource），保持 WebFlux 纯净。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
