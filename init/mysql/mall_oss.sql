-- ============================================================
-- mall_oss 对象存储服务初始化脚本
-- 包含：建库 + 文件元数据表
-- ============================================================

CREATE DATABASE IF NOT EXISTS mall_oss
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE mall_oss;

CREATE TABLE oss_file_meta (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    upload_id       VARCHAR(36)   NOT NULL COMMENT '上传 ID（UUID）',
    bucket          VARCHAR(64)   NOT NULL COMMENT 'Bucket 名称',
    object_name     VARCHAR(512)  NOT NULL COMMENT 'MinIO 对象路径',
    original_name   VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    file_size       BIGINT        NOT NULL COMMENT '文件大小（字节）',
    content_type    VARCHAR(128)  NOT NULL COMMENT 'MIME 类型',
    business_type   VARCHAR(32)   NOT NULL COMMENT '业务类型',
    file_url        VARCHAR(1024) NOT NULL COMMENT '访问 URL',
    uploader_id     BIGINT        DEFAULT NULL COMMENT '上传者用户 ID',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除 2-待确认',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bucket_object (bucket, object_name(100)),
    INDEX idx_business_type (business_type, create_time),
    INDEX idx_uploader (uploader_id, create_time),
    UNIQUE INDEX uk_upload_id (upload_id)
) ENGINE=InnoDB COMMENT='文件元数据表';
