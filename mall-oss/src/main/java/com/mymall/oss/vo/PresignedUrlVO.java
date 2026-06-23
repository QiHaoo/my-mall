package com.mymall.oss.vo;

import lombok.Builder;
import lombok.Data;

/**
 * Presigned URL 响应
 */
@Data
@Builder
public class PresignedUrlVO {

    /**
     * 上传记录 ID（回调时使用）
     */
    private String uploadId;

    /**
     * Presigned PUT URL（前端用此 URL 直传）
     */
    private String uploadUrl;

    /**
     * MinIO 中的对象路径
     */
    private String objectName;

    /**
     * Bucket 名称
     */
    private String bucket;

    /**
     * 上传完成后的访问 URL（public bucket 可直接访问）
     */
    private String fileUrl;

    /**
     * URL 有效期（秒）
     */
    private int expiresIn;
}
