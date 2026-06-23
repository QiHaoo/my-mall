package com.mymall.oss.service;

import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.common.oss.OssTemplate;
import com.mymall.common.util.UserContext;
import com.mymall.oss.dto.CallbackDTO;
import com.mymall.oss.dto.UploadPolicyDTO;
import com.mymall.oss.entity.OssFileMeta;
import com.mymall.oss.mapper.OssFileMetaMapper;
import com.mymall.oss.service.impl.OssServiceImpl;
import com.mymall.oss.vo.DownloadUrlVO;
import com.mymall.oss.vo.FileMetaVO;
import com.mymall.oss.vo.PresignedUrlVO;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OssService 纯单元测试
 * <p>
 * 不加载 Spring 上下文，Mock 所有依赖，速度极快。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("对象存储服务")
class OssServiceTest {

    @Mock
    private OssFileMetaMapper ossFileMetaMapper;

    @Mock
    private OssTemplate ossTemplate;

    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() {
        // ServiceImpl.baseMapper is set by Spring @Autowired field injection.
        // Set it manually via reflection.
        ossService = new OssServiceImpl(ossTemplate);
        ReflectionTestUtils.setField(ossService, "baseMapper", ossFileMetaMapper);

        // Set @Value fields
        ReflectionTestUtils.setField(ossService, "allowedBuckets",
                List.of("mall-product", "mall-member", "mall-comment", "mall-private"));
        ReflectionTestUtils.setField(ossService, "allowedContentTypes",
                List.of("image/jpeg", "image/png", "image/gif", "image/webp",
                        "video/mp4", "application/pdf",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        ReflectionTestUtils.setField(ossService, "maxImageSize", 10_485_760L);
        ReflectionTestUtils.setField(ossService, "maxVideoSize", 524_288_000L);
        ReflectionTestUtils.setField(ossService, "maxOtherSize", 52_428_800L);
        ReflectionTestUtils.setField(ossService, "urlExpiry", 300);
        ReflectionTestUtils.setField(ossService, "downloadExpiry", 600);
        ReflectionTestUtils.setField(ossService, "callbackExpiry", 1800);

        // buildPublicUrl 默认返回一个可用值（createUploadPolicy 内部会调用）
        lenient().when(ossTemplate.buildPublicUrl(anyString(), anyString()))
                .thenReturn("http://127.0.0.1:9000/mall-product/spu/2026/06/19/abc.jpg");
    }

    @AfterEach
    void tearDown() {
        // 清理 UserContext，防止 ThreadLocal 串号影响其它测试
        UserContext.clear();
    }

    // ==================== 签发上传凭证 ====================

    @Nested
    @DisplayName("签发上传凭证")
    class CreateUploadPolicy {

        @Test
        @DisplayName("正常签发应返回 PresignedUrlVO 并写入 PENDING 记录")
        void shouldReturnPresignedUrlWhenValid() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            when(ossTemplate.getPresignedPutUrl(eq("mall-product"), anyString(), eq(300)))
                    .thenReturn("http://127.0.0.1:9000/mall-product/spu/2026/06/19/abc.jpg?X-Amz-Signature=xxx");
            // ServiceImpl.save delegates to baseMapper.insert
            lenient().when(ossFileMetaMapper.insert(any(OssFileMeta.class))).thenReturn(1);

            // When
            PresignedUrlVO result = ossService.createUploadPolicy(dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUploadId()).isNotBlank();
            assertThat(result.getUploadUrl()).contains("X-Amz-Signature");
            assertThat(result.getBucket()).isEqualTo("mall-product");
            assertThat(result.getObjectName()).startsWith("spu/");
            assertThat(result.getObjectName()).endsWith(".jpg");
            assertThat(result.getExpiresIn()).isEqualTo(300);
            verify(ossTemplate).getPresignedPutUrl(eq("mall-product"), anyString(), eq(300));
        }

        @Test
        @DisplayName("登录用户上传时 uploader_id 应写入记录")
        void shouldWriteUploaderIdWhenLoggedIn() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            UserContext.setUserId(123L);
            when(ossTemplate.getPresignedPutUrl(anyString(), anyString(), anyInt()))
                    .thenReturn("http://mock/url");

            // 用 ArgumentCaptor 捕获写入的实体
            org.mockito.ArgumentCaptor<OssFileMeta> captor =
                    org.mockito.ArgumentCaptor.forClass(OssFileMeta.class);
            when(ossFileMetaMapper.insert(captor.capture())).thenReturn(1);

            // When
            ossService.createUploadPolicy(dto);

            // Then
            assertThat(captor.getValue().getUploaderId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("Bucket 不在白名单时应抛异常")
        void shouldThrowWhenBucketNotAllowed() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setBucket("evil-bucket");

            // When & Then
            assertThatThrownBy(() -> ossService.createUploadPolicy(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_BUCKET_NOT_ALLOWED.getCode());
        }

        @Test
        @DisplayName("ContentType 不允许时应抛异常")
        void shouldThrowWhenContentTypeNotAllowed() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setContentType("application/x-msdownload");

            // When & Then
            assertThatThrownBy(() -> ossService.createUploadPolicy(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_CONTENT_TYPE_NOT_ALLOWED.getCode());
        }

        @Test
        @DisplayName("图片超过 10MB 限制时应抛异常")
        void shouldThrowWhenImageTooLarge() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setFileSize(20_971_520L); // 20MB

            // When & Then
            assertThatThrownBy(() -> ossService.createUploadPolicy(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_FILE_TOO_LARGE.getCode());
        }

        @Test
        @DisplayName("视频 100MB 应允许通过")
        void shouldAllowLargeVideo() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setContentType("video/mp4");
            dto.setFileSize(104_857_600L); // 100MB
            lenient().when(ossTemplate.getPresignedPutUrl(anyString(), anyString(), anyInt()))
                    .thenReturn("http://mock/url");
            lenient().when(ossFileMetaMapper.insert(any(OssFileMeta.class))).thenReturn(1);

            // When
            PresignedUrlVO result = ossService.createUploadPolicy(dto);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("businessType 不在枚举中时应抛参数异常")
        void shouldThrowWhenBusinessTypeInvalid() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setBusinessType("invalid-type");

            // When & Then
            assertThatThrownBy(() -> ossService.createUploadPolicy(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("对象路径应包含正确的日期格式和扩展名")
        void shouldGenerateCorrectObjectName() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            lenient().when(ossTemplate.getPresignedPutUrl(anyString(), anyString(), anyInt()))
                    .thenReturn("http://mock/url");
            lenient().when(ossFileMetaMapper.insert(any(OssFileMeta.class))).thenReturn(1);

            // When
            PresignedUrlVO result = ossService.createUploadPolicy(dto);

            // Then
            assertThat(result.getObjectName())
                    .startsWith("spu/")
                    .endsWith(".jpg")
                    .matches("spu/\\d{4}/\\d{2}/\\d{2}/[a-f0-9]+\\.jpg");
        }
    }

    // ==================== 上传完成回调 ====================

    @Nested
    @DisplayName("上传完成回调")
    class HandleCallback {

        @Test
        @DisplayName("uploadId 不存在时应抛异常")
        void shouldThrowWhenUploadIdNotFound() {
            // Given
            CallbackDTO dto = new CallbackDTO();
            dto.setUploadId("nonexistent");
            dto.setBucket("mall-product");
            dto.setObjectName("spu/2026/06/19/abc.jpg");

            // ServiceImpl.getOne(wrapper) 内部调用 baseMapper.selectOne
            lenient().when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> ossService.handleCallback(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_UPLOAD_ID_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("MinIO statObject 返回 null 时应抛验证失败异常")
        void shouldThrowWhenMinioVerifyFails() {
            // Given
            CallbackDTO dto = buildValidCallback();
            OssFileMeta pendingMeta = buildPendingMeta();

            // Mock getOne (getOne(wrapper) -> baseMapper.selectOne)
            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(pendingMeta);
            when(ossTemplate.statObject("mall-product", "spu/2026/06/19/abc.jpg"))
                    .thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> ossService.handleCallback(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_UPLOAD_VERIFY_FAILED.getCode());
        }

        @Test
        @DisplayName("实际 Content-Type 与声明不一致时应抛验证失败异常")
        void shouldThrowWhenContentTypeMismatch() {
            // Given
            CallbackDTO dto = buildValidCallback();
            OssFileMeta pendingMeta = buildPendingMeta(); // contentType=image/jpeg

            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(pendingMeta);
            StatObjectResponse mockStat = mock(StatObjectResponse.class);
            lenient().when(mockStat.size()).thenReturn(1048576L);
            // 客户端用 application/octet-stream 直传，与声明的 image/jpeg 不符
            when(mockStat.contentType()).thenReturn("application/octet-stream");
            when(ossTemplate.statObject("mall-product", "spu/2026/06/19/abc.jpg"))
                    .thenReturn(mockStat);

            // When & Then
            assertThatThrownBy(() -> ossService.handleCallback(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_UPLOAD_VERIFY_FAILED.getCode());
        }

        @Test
        @DisplayName("回调成功时应更新状态为 ACTIVE 并返回文件信息")
        void shouldActivateAndReturnFileMetaWhenCallbackSuccess() {
            // Given
            CallbackDTO dto = buildValidCallback();
            OssFileMeta pendingMeta = buildPendingMeta();

            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(pendingMeta);
            StatObjectResponse mockStat = mock(StatObjectResponse.class);
            lenient().when(mockStat.size()).thenReturn(1048576L);
            when(mockStat.contentType()).thenReturn("image/jpeg"); // 与声明一致
            when(ossTemplate.statObject("mall-product", "spu/2026/06/19/abc.jpg"))
                    .thenReturn(mockStat);
            lenient().when(ossFileMetaMapper.updateById(any(OssFileMeta.class))).thenReturn(1);

            // When
            FileMetaVO result = ossService.handleCallback(dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getBucket()).isEqualTo("mall-product");
            assertThat(result.getObjectName()).isEqualTo("spu/2026/06/19/abc.jpg");
            assertThat(pendingMeta.getStatus()).isEqualTo(OssFileMeta.STATUS_ACTIVE);
            verify(ossFileMetaMapper).updateById(pendingMeta);
        }

        @Test
        @DisplayName("对已 ACTIVE 的记录重复回调应幂等返回，不重复校验 MinIO")
        void shouldBeIdempotentWhenAlreadyActive() {
            // Given
            CallbackDTO dto = buildValidCallback();
            OssFileMeta activeMeta = buildActiveMeta();

            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(activeMeta);

            // When
            FileMetaVO result = ossService.handleCallback(dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getObjectName()).isEqualTo("spu/2026/06/19/abc.jpg");
            // 幂等：不应再调用 MinIO statObject / updateById
            verify(ossTemplate, never()).statObject(anyString(), anyString());
            verify(ossFileMetaMapper, never()).updateById(any(OssFileMeta.class));
        }

        @Test
        @DisplayName("回调的 bucket/objectName 与签发记录不一致时应拒绝")
        void shouldRejectWhenCallbackObjectMismatch() {
            // Given
            CallbackDTO dto = buildValidCallback();
            dto.setObjectName("spu/2026/06/19/tampered.jpg"); // 篡改
            OssFileMeta pendingMeta = buildPendingMeta();

            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(pendingMeta);

            // When & Then
            assertThatThrownBy(() -> ossService.handleCallback(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_UPLOAD_VERIFY_FAILED.getCode());
        }
    }

    // ==================== 删除文件 ====================

    @Nested
    @DisplayName("删除文件")
    class DeleteFile {

        @Test
        @DisplayName("应调用 MinIO 删除并更新 DB 状态为已删除")
        void shouldDeleteFromMinioAndUpdateDb() {
            // Given
            OssFileMeta activeMeta = buildActiveMeta();
            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(activeMeta);
            lenient().when(ossFileMetaMapper.updateById(any(OssFileMeta.class))).thenReturn(1);

            // When
            ossService.deleteFile("mall-product", "spu/2026/06/19/abc.jpg");

            // Then
            verify(ossTemplate).delete("mall-product", "spu/2026/06/19/abc.jpg");
            assertThat(activeMeta.getStatus()).isEqualTo(OssFileMeta.STATUS_DELETED);
            verify(ossFileMetaMapper).updateById(activeMeta);
        }

        @Test
        @DisplayName("DB 中无记录时应仅删除 MinIO 对象不报错")
        void shouldDeleteMinioOnlyWhenNoDbRecord() {
            // Given
            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(null);

            // When
            ossService.deleteFile("mall-product", "spu/nonexistent.jpg");

            // Then
            verify(ossTemplate).delete("mall-product", "spu/nonexistent.jpg");
            verify(ossFileMetaMapper, never()).updateById(any(OssFileMeta.class));
        }

        @Test
        @DisplayName("非上传者删除他人文件时应抛 FORBIDDEN")
        void shouldRejectWhenDeleteOthersFile() {
            // Given
            OssFileMeta meta = buildActiveMeta();
            meta.setUploaderId(1L);       // 文件归属用户 1
            UserContext.setUserId(2L);    // 当前登录用户 2
            when(ossFileMetaMapper.selectOne(any(), anyBoolean())).thenReturn(meta);

            // When & Then
            assertThatThrownBy(() -> ossService.deleteFile("mall-product", "spu/2026/06/19/abc.jpg"))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
            // 被拒绝时不应删除 MinIO 对象
            verify(ossTemplate, never()).delete(anyString(), anyString());
        }
    }

    // ==================== 获取下载 URL ====================

    @Nested
    @DisplayName("获取下载 URL")
    class GetDownloadUrl {

        @Test
        @DisplayName("文件不存在时应抛异常")
        void shouldThrowWhenFileNotFound() {
            // Given
            when(ossFileMetaMapper.selectCount(any())).thenReturn(0L);

            // When & Then
            assertThatThrownBy(() -> ossService.getDownloadUrl("mall-private", "import/test.xlsx"))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_FILE_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("文件存在时应返回 Presigned GET URL")
        void shouldReturnPresignedUrlWhenFileExists() {
            // Given
            when(ossFileMetaMapper.selectCount(any())).thenReturn(1L);
            when(ossTemplate.getPresignedGetUrl("mall-private", "import/test.xlsx", 600))
                    .thenReturn("http://127.0.0.1:9000/mall-private/import/test.xlsx?X-Amz-Signature=yyy");

            // When
            DownloadUrlVO result = ossService.getDownloadUrl("mall-private", "import/test.xlsx");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDownloadUrl()).contains("X-Amz-Signature");
            assertThat(result.getExpiresIn()).isEqualTo(600);
        }
    }

    // ==================== 定时清理超时 PENDING 记录 ====================

    @Nested
    @DisplayName("清理超时 PENDING 记录")
    class CleanupExpiredPending {

        @Test
        @DisplayName("无超时记录时应直接返回不操作")
        void shouldDoNothingWhenNoExpired() {
            // Given
            when(ossFileMetaMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

            // When
            ossService.cleanupExpiredPendingRecords();

            // Then
            verify(ossTemplate, never()).delete(anyString(), anyString());
            verify(ossFileMetaMapper, never()).updateById(any(OssFileMeta.class));
        }

        @Test
        @DisplayName("存在超时 PENDING 记录时应删除孤儿对象并更新状态")
        void shouldDeleteObjectAndMarkDeletedWhenExpired() {
            // Given
            OssFileMeta pendingMeta = buildPendingMeta();
            pendingMeta.setCreateTime(LocalDateTime.now().minusHours(2)); // 超过 30 分钟阈值
            when(ossFileMetaMapper.selectList(any())).thenReturn(List.of(pendingMeta));
            lenient().when(ossFileMetaMapper.updateById(any(OssFileMeta.class))).thenReturn(1);

            // When
            ossService.cleanupExpiredPendingRecords();

            // Then
            verify(ossTemplate).delete("mall-product", "spu/2026/06/19/abc.jpg");
            assertThat(pendingMeta.getStatus()).isEqualTo(OssFileMeta.STATUS_DELETED);
        }

        @Test
        @DisplayName("单条清理失败不应中断其余记录清理")
        void shouldContinueWhenOneFails() {
            // Given
            OssFileMeta failed = buildPendingMeta();
            failed.setObjectName("spu/2026/06/19/fail.jpg");
            failed.setCreateTime(LocalDateTime.now().minusHours(2));
            OssFileMeta ok = buildPendingMeta();
            ok.setObjectName("spu/2026/06/19/ok.jpg");
            ok.setCreateTime(LocalDateTime.now().minusHours(2));

            when(ossFileMetaMapper.selectList(any())).thenReturn(List.of(failed, ok));
            // 第一条删除抛异常（delete 是 void 方法，用 doThrow stub）
            doThrow(new RuntimeException("minio error"))
                    .when(ossTemplate).delete("mall-product", "spu/2026/06/19/fail.jpg");
            lenient().when(ossFileMetaMapper.updateById(any(OssFileMeta.class))).thenReturn(1);

            // When — 不应抛异常
            assertThatCode(() -> ossService.cleanupExpiredPendingRecords()).doesNotThrowAnyException();

            // Then — 第二条仍被处理
            verify(ossTemplate).delete("mall-product", "spu/2026/06/19/ok.jpg");
            assertThat(ok.getStatus()).isEqualTo(OssFileMeta.STATUS_DELETED);
        }
    }

    // ==================== 辅助方法 ====================

    private UploadPolicyDTO buildValidPolicy() {
        UploadPolicyDTO dto = new UploadPolicyDTO();
        dto.setBucket("mall-product");
        dto.setBusinessType("spu");
        dto.setContentType("image/jpeg");
        dto.setFileSize(1_048_576L); // 1MB
        dto.setOriginalFilename("product-photo.jpg");
        return dto;
    }

    private CallbackDTO buildValidCallback() {
        CallbackDTO dto = new CallbackDTO();
        dto.setUploadId("test-upload-id");
        dto.setBucket("mall-product");
        dto.setObjectName("spu/2026/06/19/abc.jpg");
        return dto;
    }

    private OssFileMeta buildPendingMeta() {
        OssFileMeta meta = new OssFileMeta();
        meta.setId(1L);
        meta.setUploadId("test-upload-id");
        meta.setBucket("mall-product");
        meta.setObjectName("spu/2026/06/19/abc.jpg");
        meta.setOriginalName("product-photo.jpg");
        meta.setFileSize(1_048_576L);
        meta.setContentType("image/jpeg");
        meta.setBusinessType("spu");
        meta.setFileUrl("http://127.0.0.1:9000/mall-product/spu/2026/06/19/abc.jpg");
        meta.setStatus(OssFileMeta.STATUS_PENDING);
        return meta;
    }

    private OssFileMeta buildActiveMeta() {
        OssFileMeta meta = buildPendingMeta();
        meta.setStatus(OssFileMeta.STATUS_ACTIVE);
        return meta;
    }
}
