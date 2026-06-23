package com.mymall.oss.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.common.oss.OssTemplate;
import com.mymall.oss.dto.CallbackDTO;
import com.mymall.oss.dto.UploadPolicyDTO;
import com.mymall.oss.entity.OssFileMeta;
import com.mymall.oss.mapper.OssFileMetaMapper;
import com.mymall.oss.service.IOssService;
import com.mymall.oss.vo.DownloadUrlVO;
import com.mymall.oss.vo.FileMetaVO;
import com.mymall.oss.vo.PresignedUrlVO;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 对象存储服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssServiceImpl extends ServiceImpl<OssFileMetaMapper, OssFileMeta> implements IOssService {

    private final OssTemplate ossTemplate;

    /** 允许的 Bucket 白名单 */
    @Value("${oss.upload.allowed-buckets:mall-product,mall-member,mall-comment,mall-private}")
    private List<String> allowedBuckets;

    /** 允许的 ContentType 白名单 */
    @Value("${oss.upload.allowed-content-types:image/jpeg,image/png,image/gif,image/webp,video/mp4,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.openxmlformats-officedocument.wordprocessingml.document}")
    private List<String> allowedContentTypes;

    /** 图片最大大小（字节），默认 10MB */
    @Value("${oss.upload.max-image-size:10485760}")
    private long maxImageSize;

    /** 视频最大大小（字节），默认 500MB */
    @Value("${oss.upload.max-video-size:524288000}")
    private long maxVideoSize;

    /** 其他文件最大大小（字节），默认 50MB */
    @Value("${oss.upload.max-other-size:52428800}")
    private long maxOtherSize;

    /** Presigned URL 有效期（秒） */
    @Value("${oss.upload.url-expiry:300}")
    private int urlExpiry;

    /** 私有文件下载 URL 有效期（秒） */
    @Value("${oss.upload.download-expiry:600}")
    private int downloadExpiry;

    /** 允许的 businessType 枚举 */
    private static final Set<String> ALLOWED_BUSINESS_TYPES = Set.of(
            "spu", "sku", "brand", "category", "attr", "avatar", "comment", "import", "export"
    );

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // ==================== 签发上传凭证 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresignedUrlVO createUploadPolicy(UploadPolicyDTO dto) {
        // 1. 校验 Bucket 白名单
        if (!allowedBuckets.contains(dto.getBucket())) {
            throw new BizException(ResultCode.OSS_BUCKET_NOT_ALLOWED);
        }

        // 2. 校验 ContentType
        if (!allowedContentTypes.contains(dto.getContentType())) {
            throw new BizException(ResultCode.OSS_CONTENT_TYPE_NOT_ALLOWED);
        }

        // 3. 校验文件大小
        validateFileSize(dto.getContentType(), dto.getFileSize());

        // 4. 校验 businessType
        if (!ALLOWED_BUSINESS_TYPES.contains(dto.getBusinessType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的业务类型: " + dto.getBusinessType());
        }

        // 5. 生成对象路径
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String objectName = buildObjectName(dto.getBusinessType(), dto.getOriginalFilename());

        // 6. 生成 Presigned PUT URL
        String presignedUrl = ossTemplate.getPresignedPutUrl(dto.getBucket(), objectName, urlExpiry);

        // 7. 构建文件访问 URL（public bucket 可直接访问）
        String fileUrl = buildFileUrl(dto.getBucket(), objectName);

        // 8. 写入 DB（PENDING 状态，等回调确认）
        OssFileMeta meta = new OssFileMeta();
        meta.setUploadId(uploadId);
        meta.setBucket(dto.getBucket());
        meta.setObjectName(objectName);
        meta.setOriginalName(dto.getOriginalFilename());
        meta.setFileSize(dto.getFileSize());
        meta.setContentType(dto.getContentType());
        meta.setBusinessType(dto.getBusinessType());
        meta.setFileUrl(fileUrl);
        meta.setStatus(OssFileMeta.STATUS_PENDING);
        save(meta);

        return PresignedUrlVO.builder()
                .uploadId(uploadId)
                .uploadUrl(presignedUrl)
                .objectName(objectName)
                .bucket(dto.getBucket())
                .fileUrl(fileUrl)
                .expiresIn(urlExpiry)
                .build();
    }

    // ==================== 上传完成回调 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileMetaVO handleCallback(CallbackDTO dto) {
        // 1. 查找 uploadId 记录
        OssFileMeta meta = getOne(new LambdaQueryWrapper<OssFileMeta>()
                .eq(OssFileMeta::getUploadId, dto.getUploadId())
                .eq(OssFileMeta::getStatus, OssFileMeta.STATUS_PENDING));

        if (meta == null) {
            throw new BizException(ResultCode.OSS_UPLOAD_ID_NOT_FOUND);
        }

        // 2. 验证 MinIO 中文件已上传
        StatObjectResponse stat = ossTemplate.statObject(dto.getBucket(), dto.getObjectName());
        if (stat == null) {
            throw new BizException(ResultCode.OSS_UPLOAD_VERIFY_FAILED, "MinIO 中未找到文件");
        }

        // 3. 更新状态为 ACTIVE，补充实际文件大小
        meta.setStatus(OssFileMeta.STATUS_ACTIVE);
        meta.setFileSize(stat.size());
        updateById(meta);

        return toFileMetaVO(meta);
    }

    // ==================== 删除文件 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String bucket, String objectName) {
        // 1. 删除 MinIO 对象
        ossTemplate.delete(bucket, objectName);

        // 2. 更新 DB 状态为已删除
        OssFileMeta meta = getOne(new LambdaQueryWrapper<OssFileMeta>()
                .eq(OssFileMeta::getBucket, bucket)
                .eq(OssFileMeta::getObjectName, objectName)
                .ne(OssFileMeta::getStatus, OssFileMeta.STATUS_DELETED)
                .last("LIMIT 1"));

        if (meta != null) {
            meta.setStatus(OssFileMeta.STATUS_DELETED);
            updateById(meta);
        }
    }

    // ==================== 获取下载 URL ====================

    @Override
    public DownloadUrlVO getDownloadUrl(String bucket, String objectName) {
        // 校验文件记录存在
        long count = count(new LambdaQueryWrapper<OssFileMeta>()
                .eq(OssFileMeta::getBucket, bucket)
                .eq(OssFileMeta::getObjectName, objectName)
                .eq(OssFileMeta::getStatus, OssFileMeta.STATUS_ACTIVE));

        if (count == 0) {
            throw new BizException(ResultCode.OSS_FILE_NOT_FOUND);
        }

        String url = ossTemplate.getPresignedGetUrl(bucket, objectName, downloadExpiry);
        return DownloadUrlVO.builder()
                .downloadUrl(url)
                .expiresIn(downloadExpiry)
                .build();
    }

    // ==================== 内部方法 ====================

    /**
     * 校验文件大小
     */
    private void validateFileSize(String contentType, long fileSize) {
        long maxSize;
        if (contentType.startsWith("image/")) {
            maxSize = maxImageSize;
        } else if (contentType.startsWith("video/")) {
            maxSize = maxVideoSize;
        } else {
            maxSize = maxOtherSize;
        }

        if (fileSize > maxSize) {
            throw new BizException(ResultCode.OSS_FILE_TOO_LARGE,
                    "文件大小 %d 字节超过限制 %d 字节".formatted(fileSize, maxSize));
        }
    }

    /**
     * 构建对象路径：{businessType}/{yyyy/MM/dd}/{uuid}.{ext}
     */
    private String buildObjectName(String businessType, String originalFilename) {
        String datePath = LocalDate.now().format(DATE_PATH);
        String ext = extractExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return businessType + "/" + datePath + "/" + uuid + ext;
    }

    /**
     * 提取文件扩展名（含点号）
     */
    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0) {
            return filename.substring(dotIndex);
        }
        return "";
    }

    /**
     * 构建文件访问 URL（基于 MinIO endpoint + bucket + objectName）
     */
    private String buildFileUrl(String bucket, String objectName) {
        // 对于 public bucket，文件可通过 MinIO endpoint 直接 GET
        // 这里返回相对路径格式，前端可根据环境拼接完整 URL
        return "/" + bucket + "/" + objectName;
    }

    private FileMetaVO toFileMetaVO(OssFileMeta meta) {
        return FileMetaVO.builder()
                .fileId(meta.getId())
                .fileUrl(meta.getFileUrl())
                .objectName(meta.getObjectName())
                .bucket(meta.getBucket())
                .originalName(meta.getOriginalName())
                .fileSize(meta.getFileSize())
                .contentType(meta.getContentType())
                .businessType(meta.getBusinessType())
                .build();
    }
}
