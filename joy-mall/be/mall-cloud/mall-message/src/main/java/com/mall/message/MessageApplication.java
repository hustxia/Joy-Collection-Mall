package com.mall.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消息服务（V14，8209）：模板渲染 + 通道发送 + 失败重试 + 降级站内信。
 *
 * <p>对外只暴露 {@code POST /inner/message/send}（服务间调用，不经网关）；
 * order / refund 侧接线见报告「未完成/降级项」——本期先交付通道与契约，不改主链路。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper"})
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
