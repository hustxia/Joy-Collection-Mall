package com.mall.controller;

import com.mall.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：后台首页的「连通检测」按钮打这里（走网关 → 系统域 8201）。
 * 返回当前服务名与端口，方便在微服务下确认请求到底落到了哪个进程。
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @Value("${spring.application.name:mall-system}")
    private String appName;

    @Value("${server.port:8201}")
    private String port;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", appName);
        body.put("port", port);
        body.put("time", LocalDateTime.now().toString());
        body.put("status", "UP");
        return Result.ok(body);
    }
}
