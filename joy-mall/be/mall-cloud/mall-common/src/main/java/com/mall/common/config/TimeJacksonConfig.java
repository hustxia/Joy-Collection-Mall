package com.mall.common.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间序列化统一格式：LocalDateTime 输出 yyyy-MM-dd HH:mm:ss。
 * 由 codegen-server 脚手架生成。
 */
@Configuration
public class TimeJacksonConfig {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Module localDateTimeModule() {
        return new SimpleModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER))
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
    }
}
