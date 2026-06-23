package com.mymall.oss.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.common.oss.OssTemplate;
import com.mymall.common.util.UserContext;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 对象存储服务实现
 *
 * <p>事务边界说明：MinIO 网络调用（签发/删除/statObject）一律放在事务外，避免长时间持有 DB 连接；
 * 方法内 DB 操作均为单条 insert/update，天然原子，无需声明事务。
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

    /** Presigned PUT URL 有效期（秒） */
    @Value("${oss.upload.url-expiry:300}")
    private int urlExpiry;

    /** 私有文件下载 URL 有效期（秒） */
    @Value("${oss.upload.download-expiry:600}")
    private int downloadExpiry;

    /** 上传凭证回调有效期（秒）：超过未回调的 PENDING 记录由定时任务清理 */
    @Value("${oss.upload.callback-expiry:1800}")
    private int callbackExpiry;

    /** 允许的 businessType 枚举 */
    private static final Set<String> ALLOWED_BUSINESS_TYPES = Set.of(
            "spu", "sku", "brand", "category", "attr", "avatar", "comment", "import", "export"
    );

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // ==================== 签发上传凭证 ====================

    @Override
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

        // 5. 生成对象路径与上传 ID
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String objectName = buildObjectName(dto.getBusinessType(), dto.getOriginalFilename());

        // 6. 生成 Presigned PUT URL（MinIO 网络调用，放事务外）
        String presignedUrl = ossTemplate.getPresignedPutUrl(dto.getBucket(), objectName, urlExpiry);

        // 7. 构建文件访问 URL（基于 publicBaseUrl，public bucket 可直接访问）
        String fileUrl = ossTemplate.buildPublicUrl(dto.getBucket(), objectName);

        // 8. 写入 DB（PENDING 状态，等回调确认；uploader_id 取自登录上下文）
        OssFileMeta meta = new OssFileMeta();
        meta.setUploadId(uploadId);
        meta.setBucket(dto.getBucket());
        meta.setObjectName(objectName);
        meta.setOriginalName(dto.getOriginalFilename());
        meta.setFileSize(dto.getFileSize());
        meta.setContentType(dto.getContentType());
        meta.setBusinessType(dto.getBusinessType());
        meta.setFileUrl(fileUrl);
        meta.setUploaderId(UserContext.getUserId());
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
    public FileMetaVO handleCallback(CallbackDTO dto) {
        // 1. 按 uploadId 查记录（uploadId 全局唯一）
        OssFileMeta meta = getOne(new LambdaQueryWrapper<OssFileMeta>()
                .eq(OssFileMeta::getUploadId, dto.getUploadId())
                .last("LIMIT 1"));

        if (meta == null) {
            throw new BizException(ResultCode.OSS_UPLOAD_ID_NOT_FOUND);
        }

        // 2. 幂等：已激活的记录直接返回，不重复校验 MinIO（前端重试安全）
        if (meta.getStatus() == OssFileMeta.STATUS_ACTIVE) {
            return toFileMetaVO(meta);
        }

        // 仅 PENDING 状态可推进；DELETED 等其他状态视为异常
        if (meta.getStatus() != OssFileMeta.STATUS_PENDING) {
            throw new BizException(ResultCode.OSS_UPLOAD_ID_EXPIRED);
        }

        // 3. 校验 bucket/objectName 与签发时一致，防止伪造回调
        if (!meta.getBucket().equals(dto.getBucket()) || !meta.getObjectName().equals(dto.getObjectName())) {
            throw new BizException(ResultCode.OSS_UPLOAD_VERIFY_FAILED, "回调对象与签发记录不一致");
        }

        // 4. statObject 确认文件已上传，并校验实际 Content-Type 与声明一致
        //    （弥补 Presigned URL 未把 Content-Type 钉入签名的安全缺口：客户端若用其它类型直传，此处拒绝）
        StatObjectResponse stat = ossTemplate.statObject(dto.getBucket(), dto.getObjectName());
        if (stat == null) {
            throw new BizException(ResultCode.OSS_UPLOAD_VERIFY_FAILED, "MinIO 中未找到文件");
        }
        if (!contentTypeEquals(stat.contentType(), meta.getContentType())) {
            throw new BizException(ResultCode.OSS_UPLOAD_VERIFY_FAILED,
                    "文件类型与声明不一致: 实际=%s, 声明=%s".formatted(stat.contentType(), meta.getContentType()));
        }

        // 5. 更新状态为 ACTIVE，用 MinIO 实际大小覆盖（声明的 fileSize 仅作预校验）
        meta.setStatus(OssFileMeta.STATUS_ACTIVE);
        meta.setFileSize(stat.size());
        updateById(meta);

        return toFileMetaVO(meta);
    }

    // ==================== 删除文件 ====================

    @Override
    public void deleteFile(String bucket, String objectName) {
        // 1. 查 DB 记录（未删除的），用于归属校验
        OssFileMeta meta = getOne(new LambdaQueryWrapper<OssFileMeta>()
                .eq(OssFileMeta::getBucket, bucket)
                .eq(OssFileMeta::getObjectName, objectName)
                .ne(OssFileMeta::getStatus, OssFileMeta.STATUS_DELETED)
                .last("LIMIT 1"));

        // 2. 越权校验：普通用户只能删自己上传的文件；后台/服务账号（无登录态）放行，由网关侧 RBAC 控制
        Long currentUserId = UserContext.getUserId();
        if (meta != null && currentUserId != null && meta.getUploaderId() != null
                && !currentUserId.equals(meta.getUploaderId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权删除他人上传的文件");
        }

        // 3. 删除 MinIO 对象（NoSuchKey 视为成功，见 OssTemplate.delete）
        ossTemplate.delete(bucket, objectName);

        // 4. 更新 DB 状态为已删除
        if (meta != null) {
            meta.setStatus(OssFileMeta.STATUS_DELETED);
            updateById(meta);
        }
    }

    // ==================== 获取下载 URL ====================

    @Override
    public DownloadUrlVO getDownloadUrl(String bucket, String objectName) {
        // 校验文件记录存在且处于正常状态
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

    // ==================== 定时清理超时 PENDING 记录 ====================

    /**
     * 定时清理超时未回调的 PENDING 上传记录。
     *
     * <p>前端拿了 Presigned URL 但未上传 / 未回调，会留下 PENDING 脏数据并可能遗留 MinIO 孤儿对象。
     * 默认每 1 小时执行一次，清理创建时间超过 {@link #callbackExpiry} 的 PENDING 记录：
     * 删除对应的 MinIO 对象（若已上传），并将 DB 状态置为已删除。
     */
    @Scheduled(fixedDelayString = "${oss.upload.cleanup-interval:3600000}")
    public void cleanupExpiredPendingRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(callbackExpiry);
        List<OssFileMeta> expired = list(new LambdaQueryWrapper<OssFileMeta>()
                .eq(OssFileMeta::getStatus, OssFileMeta.STATUS_PENDING)
                .lt(OssFileMeta::getCreateTime, threshold));

        if (expired.isEmpty()) {
            return;
        }

        log.info("清理超时 PENDING 上传记录: count={}", expired.size());
        for (OssFileMeta meta : expired) {
            try {
                // 删除可能已上传的孤儿对象；未上传时 NoSuchKey 被静默处理
                ossTemplate.delete(meta.getBucket(), meta.getObjectName());
                meta.setStatus(OssFileMeta.STATUS_DELETED);
                updateById(meta);
            } catch (Exception e) {
                // 单条失败不影响其余清理，下轮继续
                log.warn("清理 PENDING 记录失败: uploadId={}, object={}", meta.getUploadId(), meta.getObjectName(), e);
            }
        }
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
     * Content-Type 一致性比较：忽略大小写与 charset 后缀。
     * S3 存储时可能附加 {@code ;charset=...}，仅比较主类型。
     */
    private boolean contentTypeEquals(String actual, String declared) {
        if (actual == null || declared == null) {
            return false;
        }
        return normalizeContentType(actual).equals(normalizeContentType(declared));
    }

    private String normalizeContentType(String contentType) {
        int semi = contentType.indexOf(';');
        String main = (semi >= 0) ? contentType.substring(0, semi) : contentType;
        return main.trim().toLowerCase();
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
