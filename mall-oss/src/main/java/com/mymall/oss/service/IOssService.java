package com.mymall.oss.service;

import com.mymall.oss.dto.CallbackDTO;
import com.mymall.oss.dto.UploadPolicyDTO;
import com.mymall.oss.vo.DownloadUrlVO;
import com.mymall.oss.vo.FileMetaVO;
import com.mymall.oss.vo.PresignedUrlVO;

/**
 * 对象存储服务接口
 */
public interface IOssService {

    /**
     * 签发上传凭证（Presigned PUT URL）
     */
    PresignedUrlVO createUploadPolicy(UploadPolicyDTO dto);

    /**
     * 上传完成回调 — 确认文件已上传并写入元数据
     */
    FileMetaVO handleCallback(CallbackDTO dto);

    /**
     * 删除文件（MinIO + DB 标记删除）
     */
    void deleteFile(String bucket, String objectName);

    /**
     * 获取私有文件下载 URL（Presigned GET URL）
     */
    DownloadUrlVO getDownloadUrl(String bucket, String objectName);
}
