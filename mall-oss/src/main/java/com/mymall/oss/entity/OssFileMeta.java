package com.mymall.oss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件元数据实体
 */
@Getter
@Setter
@ToString
@TableName("oss_file_meta")
public class OssFileMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态：已删除
     */
    public static final int STATUS_DELETED = 0;

    /**
     * 状态：正常
     */
    public static final int STATUS_ACTIVE = 1;

    /**
     * 状态：待确认（上传中，尚未回调）
     */
    public static final int STATUS_PENDING = 2;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传 ID（UUID）
     */
    private String uploadId;

    /**
     * Bucket 名称
     */
    private String bucket;

    /**
     * MinIO 对象路径
     */
    private String objectName;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME 类型
     */
    private String contentType;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 访问 URL
     */
    private String fileUrl;

    /**
     * 上传者用户 ID
     */
    private Long uploaderId;

    /**
     * 状态：1-正常 0-已删除 2-待确认
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
