package com.mymall.oss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 获取上传凭证请求
 */
@Data
public class UploadPolicyDTO {

    /**
     * 目标 Bucket
     */
    @NotBlank(message = "bucket 不能为空")
    private String bucket;

    /**
     * 业务类型（spu / sku / brand / category / attr / avatar / comment / import / export）
     */
    @NotBlank(message = "businessType 不能为空")
    private String businessType;

    /**
     * 文件 MIME 类型
     */
    @NotBlank(message = "contentType 不能为空")
    private String contentType;

    /**
     * 文件大小（字节）
     */
    @NotNull(message = "fileSize 不能为空")
    @Positive(message = "fileSize 必须大于 0")
    private Long fileSize;

    /**
     * 原始文件名
     */
    @NotBlank(message = "originalFilename 不能为空")
    private String originalFilename;
}
