package com.mymall.common.oss;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 自动配置
 *
 * <p>仅当 classpath 存在 {@link MinioClient} 且配置了 {@code oss.minio.endpoint} 时生效。
 * 业务服务引入 mall-common 后，只需在 yml 中配置 oss.minio.* 即可获得 OssTemplate Bean。
 */
@Configuration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "oss.minio", name = "endpoint")
@EnableConfigurationProperties(OssProperties.class)
public class OssAutoConfiguration {

    @Bean
    public MinioClient minioClient(OssProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    public OssTemplate ossTemplate(MinioClient minioClient) {
        return new OssTemplate(minioClient);
    }
}
