package com.mymall.oss.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 上传完成回调请求
 */
@Data
public class CallbackDTO {

    /**
     * 上传 ID（签发时返回的 uploadId）
     */
    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    /**
     * Bucket 名称
     */
    @NotBlank(message = "bucket 不能为空")
    private String bucket;

    /**
     * MinIO 对象路径
     */
    @NotBlank(message = "objectName 不能为空")
    private String objectName;
}
