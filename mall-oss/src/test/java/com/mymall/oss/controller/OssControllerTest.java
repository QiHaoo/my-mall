package com.mymall.oss.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.GlobalExceptionHandler;
import com.mymall.common.exception.ResultCode;
import com.mymall.oss.dto.CallbackDTO;
import com.mymall.oss.dto.UploadPolicyDTO;
import com.mymall.oss.service.IOssService;
import com.mymall.oss.vo.DownloadUrlVO;
import com.mymall.oss.vo.FileMetaVO;
import com.mymall.oss.vo.PresignedUrlVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OssController 切片测试
 * <p>
 * 验证 HTTP 层行为：路由、参数校验、响应序列化、异常处理。
 * <p>
 * 显式 @Import GlobalExceptionHandler：@WebMvcTest 仅装载指定 Controller，不会扫描
 * @RestControllerAdvice，需手动引入才能验证 BizException → 统一错误码的转换。
 */
@WebMvcTest(OssController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("对象存储 Controller")
class OssControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IOssService ossService;

    // ==================== 签发上传凭证 ====================

    @Nested
    @DisplayName("POST /oss/policy")
    class CreateUploadPolicy {

        @Test
        @DisplayName("正常请求应返回 200 和 PresignedUrlVO")
        void shouldReturnPresignedUrlWhenValid() throws Exception {
            // Given
            PresignedUrlVO vo = PresignedUrlVO.builder()
                    .uploadId("test-id")
                    .uploadUrl("http://127.0.0.1:9000/mall-product/spu/2026/06/19/abc.jpg?sig=xxx")
                    .objectName("spu/2026/06/19/abc.jpg")
                    .bucket("mall-product")
                    .fileUrl("/mall-product/spu/2026/06/19/abc.jpg")
                    .expiresIn(300)
                    .build();
            when(ossService.createUploadPolicy(any(UploadPolicyDTO.class))).thenReturn(vo);

            // When & Then
            mockMvc.perform(post("/oss/policy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidPolicy())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.uploadId").value("test-id"))
                    .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
                    .andExpect(jsonPath("$.data.bucket").value("mall-product"))
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
        }

        @Test
        @DisplayName("缺少 bucket 时应返回 400")
        void shouldReturn400WhenBucketMissing() throws Exception {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setBucket(null);

            // When & Then
            mockMvc.perform(post("/oss/policy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("缺少 fileSize 时应返回 400")
        void shouldReturn400WhenFileSizeMissing() throws Exception {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setFileSize(null);

            // When & Then
            mockMvc.perform(post("/oss/policy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Bucket 不在白名单时应返回对应错误码")
        void shouldReturnErrorWhenBucketNotAllowed() throws Exception {
            // Given
            when(ossService.createUploadPolicy(any(UploadPolicyDTO.class)))
                    .thenThrow(new BizException(ResultCode.OSS_BUCKET_NOT_ALLOWED));

            // When & Then
            mockMvc.perform(post("/oss/policy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidPolicy())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.OSS_BUCKET_NOT_ALLOWED.getCode()));
        }

        @Test
        @DisplayName("文件超过大小限制时应返回对应错误码")
        void shouldReturnErrorWhenFileTooLarge() throws Exception {
            // Given
            when(ossService.createUploadPolicy(any(UploadPolicyDTO.class)))
                    .thenThrow(new BizException(ResultCode.OSS_FILE_TOO_LARGE));

            // When & Then
            mockMvc.perform(post("/oss/policy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidPolicy())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.OSS_FILE_TOO_LARGE.getCode()));
        }
    }

    // ==================== 上传完成回调 ====================

    @Nested
    @DisplayName("POST /oss/callback")
    class HandleCallback {

        @Test
        @DisplayName("正常回调应返回 200 和文件元数据")
        void shouldReturnFileMetaWhenValid() throws Exception {
            // Given
            FileMetaVO vo = FileMetaVO.builder()
                    .fileId(1L)
                    .fileUrl("/mall-product/spu/2026/06/19/abc.jpg")
                    .objectName("spu/2026/06/19/abc.jpg")
                    .bucket("mall-product")
                    .build();
            when(ossService.handleCallback(any(CallbackDTO.class))).thenReturn(vo);

            // When & Then
            mockMvc.perform(post("/oss/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidCallback())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.fileId").value(1))
                    .andExpect(jsonPath("$.data.bucket").value("mall-product"));
        }

        @Test
        @DisplayName("uploadId 不存在时应返回对应错误码")
        void shouldReturnErrorWhenUploadIdNotFound() throws Exception {
            // Given
            when(ossService.handleCallback(any(CallbackDTO.class)))
                    .thenThrow(new BizException(ResultCode.OSS_UPLOAD_ID_NOT_FOUND));

            // When & Then
            mockMvc.perform(post("/oss/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidCallback())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.OSS_UPLOAD_ID_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("缺少 uploadId 时应返回 400")
        void shouldReturn400WhenUploadIdMissing() throws Exception {
            // Given
            CallbackDTO dto = new CallbackDTO();
            dto.setBucket("mall-product");
            dto.setObjectName("spu/2026/06/19/abc.jpg");

            // When & Then
            mockMvc.perform(post("/oss/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 删除文件 ====================

    @Nested
    @DisplayName("DELETE /oss/file")
    class DeleteFile {

        @Test
        @DisplayName("正常删除应返回 200")
        void shouldReturn200WhenDeleted() throws Exception {
            // When & Then
            mockMvc.perform(delete("/oss/file")
                            .param("bucket", "mall-product")
                            .param("objectName", "spu/2026/06/19/abc.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(ossService).deleteFile("mall-product", "spu/2026/06/19/abc.jpg");
        }

        @Test
        @DisplayName("缺少 bucket 参数时应返回 400")
        void shouldReturn400WhenBucketMissing() throws Exception {
            // When & Then
            mockMvc.perform(delete("/oss/file")
                            .param("objectName", "spu/2026/06/19/abc.jpg"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 获取下载 URL ====================

    @Nested
    @DisplayName("GET /oss/download-url")
    class GetDownloadUrl {

        @Test
        @DisplayName("文件存在时应返回 200 和 Presigned URL")
        void shouldReturnPresignedUrl() throws Exception {
            // Given
            DownloadUrlVO vo = DownloadUrlVO.builder()
                    .downloadUrl("http://127.0.0.1:9000/mall-private/test.xlsx?sig=yyy")
                    .expiresIn(600)
                    .build();
            when(ossService.getDownloadUrl("mall-private", "import/test.xlsx")).thenReturn(vo);

            // When & Then
            mockMvc.perform(get("/oss/download-url")
                            .param("bucket", "mall-private")
                            .param("objectName", "import/test.xlsx"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").value(600));
        }

        @Test
        @DisplayName("文件不存在时应返回对应错误码")
        void shouldReturnErrorWhenFileNotFound() throws Exception {
            // Given
            when(ossService.getDownloadUrl(anyString(), anyString()))
                    .thenThrow(new BizException(ResultCode.OSS_FILE_NOT_FOUND));

            // When & Then
            mockMvc.perform(get("/oss/download-url")
                            .param("bucket", "mall-private")
                            .param("objectName", "nonexistent.xlsx"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.OSS_FILE_NOT_FOUND.getCode()));
        }
    }

    // ==================== 辅助方法 ====================

    private UploadPolicyDTO buildValidPolicy() {
        UploadPolicyDTO dto = new UploadPolicyDTO();
        dto.setBucket("mall-product");
        dto.setBusinessType("spu");
        dto.setContentType("image/jpeg");
        dto.setFileSize(1_048_576L);
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
}
