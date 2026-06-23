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
     * MinIO 服务端地址（内部/服务端访问），如 {@code http://127.0.0.1:9000}
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 公网访问基础地址（用于拼接 public bucket 文件的对外访问 URL）。
     * <p>生产环境应配置为 CDN 或独立公网域名，如 {@code https://img.mall.com}，
     * 避免把 MinIO 内网地址暴露给前端。留空时回退到 {@link #endpoint}。
     */
    private String publicBaseUrl;

    /**
     * Region。MinIO 本地可不填；迁移到 AWS S3 / 阿里云 OSS S3 兼容模式时必填，否则签名失败。
     */
    private String region;

    /**
     * 访问密钥
     */
    private String accessKey = "minioadmin";

    /**
     * 私有密钥
     */
    private String secretKey = "minioadmin123";
}
