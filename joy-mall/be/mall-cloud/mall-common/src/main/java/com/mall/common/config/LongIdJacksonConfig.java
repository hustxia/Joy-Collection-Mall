package com.mall.common.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 雪花 ID 序列化保护。
 *
 * <p>主键为 19 位雪花号，超出 JS Number.MAX_SAFE_INTEGER。若以 JSON number 下发，
 * 前端会静默截断，回传后端即查不到记录（详情 null / 删除 404 / 更新无效）。
 * 这里统一把「名为 id 或以 Id 结尾」的 Long 字段序列化为字符串。
 */
@Configuration
public class LongIdJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdToStringCustomizer() {
        SimpleModule module = new SimpleModule("long-id-to-string-module");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                             BeanDescription beanDesc,
                                                             List<BeanPropertyWriter> beanProperties) {
                for (BeanPropertyWriter writer : beanProperties) {
                    if (!isIdField(writer.getName())) {
                        continue;
                    }
                    Class<?> raw = writer.getType() == null ? null : writer.getType().getRawClass();
                    // 字段上已有 @JsonSerialize 的不能再 assign，否则抛 Cannot override _serializer
                    if ((raw == Long.class || raw == long.class) && writer.getSerializer() == null) {
                        writer.assignSerializer(ToStringSerializer.instance);
                    }
                }
                return beanProperties;
            }
        });
        return builder -> builder.postConfigurer(objectMapper -> objectMapper.registerModule(module));
    }

    private boolean isIdField(String name) {
        return "id".equals(name) || (name.length() > 2 && name.endsWith("Id"));
    }
}
