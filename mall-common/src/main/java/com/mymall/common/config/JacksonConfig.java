package com.mymall.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置
 *
 * <ul>
 *   <li>Long → String：雪花算法生成的 19 位 Long ID 在前端 JS 会精度丢失（Number 最大安全整数 2^53-1），
 *       统一序列化为字符串。代价：非 ID 的 Long（如文件字节数）也会变成字符串，前端按字符串数字处理即可。</li>
 *   <li>LocalDateTime → "yyyy-MM-dd HH:mm:ss"、LocalDate → "yyyy-MM-dd"：避免输出时间戳数组，前端可直接展示。</li>
 *   <li>反序列化时未知字段忽略，避免前端多传字段报错。</li>
 * </ul>
 *
 * <p>使用 Jackson2ObjectMapperBuilderCustomizer，在 Spring Boot 默认自动配置基础上增量定制，
 * 不破坏框架对 ObjectMapper 的其他默认行为。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Long → String（含 long 基础类型）
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.modules(longModule);

            // 时间格式化
            JavaTimeModule timeModule = new JavaTimeModule();
            timeModule.addSerializer(LocalDateTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            timeModule.addSerializer(LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(DATE_FORMATTER));
            timeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
            timeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
            builder.modules(timeModule);

            builder.failOnUnknownProperties(false);
        };
    }
}
