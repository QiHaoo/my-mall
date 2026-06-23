package com.mymall.oss.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文件元数据响应
 */
@Data
@Builder
public class FileMetaVO {

    private Long fileId;
    private String fileUrl;
    private String objectName;
    private String bucket;
    private String originalName;
    private Long fileSize;
    private String contentType;
    private String businessType;
}
