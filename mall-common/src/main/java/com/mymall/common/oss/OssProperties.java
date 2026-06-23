package com.mymall.common.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置属性
 *
 * <p>通过 {@code oss.minio.*} 前缀绑定，通常由 Nacos 配置注入。
 */
@Data
@ConfigurationProperties(prefix = "oss.minio")
public class OssProperties {

    /**
     * MinIO 服务端地址，如 {@code http://127.0.0.1:9000}
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 访问密钥
     */
    private String accessKey = "minioadmin";

    /**
     * 私有密钥
     */
    private String secretKey = "minioadmin123";
}
