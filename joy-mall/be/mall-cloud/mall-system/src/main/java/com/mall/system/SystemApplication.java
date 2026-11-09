package com.mall.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * 系统服务：后台用户 / 角色 / 菜单 / 字典 / 日志 / 文件 / 工作流 / 仪表盘 / 文件上传。
 *
 * <p>MapperScan 额外覆盖 {@code com.mall.system}（OpeLogMapper）与
 * {@code com.mall.common.file}（FileMapper）—— 这两处 Mapper 落在公共包下。
 */
@SpringBootApplication(scanBasePackages = "com.mall")
@MapperScan({"com.mall.mapper", "com.mall.system", "com.mall.common.file"})
@ServletComponentScan
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
