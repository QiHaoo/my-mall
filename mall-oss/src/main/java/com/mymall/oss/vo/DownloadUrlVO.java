package com.mymall.oss.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 下载 URL 响应
 */
@Data
@Builder
public class DownloadUrlVO {

    /**
     * Presigned GET URL
     */
    private String downloadUrl;

    /**
     * URL 有效期（秒）
     */
    private int expiresIn;
}
