package com.mymall.common.oss;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对象存储操作模板
 *
 * <p>封装 MinIO Java Client 的常用操作，业务服务通过注入此 Bean 完成文件操作，
 * 无需直接接触 {@link MinioClient}，从而实现存储引擎的可替换。
 */
@Slf4j
public class OssTemplate {

    private final MinioClient minioClient;
    private final OssProperties properties;

    public OssTemplate(MinioClient minioClient, OssProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 上传 InputStream 到指定 Bucket
     *
     * @param bucket      Bucket 名称
     * @param objectName  对象路径（如 {@code spu/2026/06/23/xxx.jpg}）
     * @param stream      文件输入流
     * @param size        已知文件大小（字节），未知传 -1（走分片上传）
     * @param contentType MIME 类型
     * @return 对象路径
     */
    public String upload(String bucket, String objectName, InputStream stream, long size, String contentType) {
        try {
            // size 未知时 partSize 传 -1，MinIO SDK 内部按 5MB 分片
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
            log.debug("文件上传成功: bucket={}, object={}", bucket, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 Presigned PUT URL（前端直传）
     *
     * @param bucket        Bucket 名称
     * @param objectName    对象路径
     * @param expirySeconds URL 有效期（秒）
     * @return Presigned URL 字符串
     */
    public String getPresignedPutUrl(String bucket, String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("生成 Presigned PUT URL 失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("生成上传 URL 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 Presigned GET URL（私有文件下载）
     *
     * @param bucket        Bucket 名称
     * @param objectName    对象路径
     * @param expirySeconds URL 有效期（秒）
     * @return Presigned URL 字符串
     */
    public String getPresignedGetUrl(String bucket, String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("生成 Presigned GET URL 失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("生成下载 URL 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除单个文件
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象路径
     */
    public void delete(String bucket, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.debug("文件删除成功: bucket={}, object={}", bucket, objectName);
        } catch (io.minio.errors.ErrorResponseException e) {
            // 对象不存在视为删除成功（幂等删除），常用于清理未完成上传的 PENDING 记录
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.debug("对象不存在，视为删除成功: bucket={}, object={}", bucket, objectName);
                return;
            }
            log.error("文件删除失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("文件删除失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量删除文件
     *
     * @param bucket      Bucket 名称
     * @param objectNames 对象路径列表
     */
    public void batchDelete(String bucket, List<String> objectNames) {
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(bucket)
                        .objects(objectNames.stream()
                                .map(DeleteObject::new)
                                .toList())
                        .build());
        for (Result<DeleteError> result : results) {
            try {
                DeleteError error = result.get();
                log.warn("批量删除中部分失败: object={}, error={}", error.objectName(), error.message());
            } catch (Exception e) {
                log.error("批量删除结果解析异常", e);
            }
        }
    }

    /**
     * 检查文件是否存在并返回元信息（statObject）
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象路径
     * @return 文件元信息，文件不存在时返回 null
     */
    public StatObjectResponse statObject(String bucket, String objectName) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (io.minio.errors.ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return null;
            }
            log.error("statObject 失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("文件检查失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("statObject 失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("文件检查失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 public bucket 文件的对外访问 URL。
     * <p>优先使用 {@link OssProperties#getPublicBaseUrl()}（CDN/公网域名），
     * 留空时回退到 {@link OssProperties#getEndpoint()}，避免暴露内网地址由调用方按需配置。
     *
     * @param bucket     Bucket 名称
     * @param objectName 对象路径
     * @return 完整访问 URL
     */
    public String buildPublicUrl(String bucket, String objectName) {
        String base = (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank())
                ? properties.getPublicBaseUrl()
                : properties.getEndpoint();
        return base + "/" + bucket + "/" + objectName;
    }
}
