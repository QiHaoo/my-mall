package com.mymall.oss.controller;

import com.mymall.common.result.R;
import com.mymall.oss.dto.CallbackDTO;
import com.mymall.oss.dto.UploadPolicyDTO;
import com.mymall.oss.service.IOssService;
import com.mymall.oss.vo.DownloadUrlVO;
import com.mymall.oss.vo.FileMetaVO;
import com.mymall.oss.vo.PresignedUrlVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 对象存储管理接口
 */
@Slf4j
@RestController
@RequestMapping("/oss")
@RequiredArgsConstructor
@Tag(name = "对象存储管理")
public class OssController {

    private final IOssService ossService;

    @Operation(summary = "获取上传凭证（Presigned PUT URL）")
    @PostMapping("/policy")
    public R<PresignedUrlVO> createUploadPolicy(@Validated @RequestBody UploadPolicyDTO dto) {
        log.info("签发上传凭证: bucket={}, businessType={}, contentType={}", dto.getBucket(), dto.getBusinessType(), dto.getContentType());
        return R.ok(ossService.createUploadPolicy(dto));
    }

    @Operation(summary = "上传完成回调")
    @PostMapping("/callback")
    public R<FileMetaVO> handleCallback(@Validated @RequestBody CallbackDTO dto) {
        log.info("上传回调: uploadId={}, bucket={}", dto.getUploadId(), dto.getBucket());
        return R.ok(ossService.handleCallback(dto));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/file")
    public R<Void> deleteFile(@RequestParam String bucket, @RequestParam String objectName) {
        log.info("删除文件: bucket={}, objectName={}", bucket, objectName);
        ossService.deleteFile(bucket, objectName);
        return R.ok(null);
    }

    @Operation(summary = "获取下载 URL（私有文件）")
    @GetMapping("/download-url")
    public R<DownloadUrlVO> getDownloadUrl(@RequestParam String bucket, @RequestParam String objectName) {
        log.info("获取下载 URL: bucket={}, objectName={}", bucket, objectName);
        return R.ok(ossService.getDownloadUrl(bucket, objectName));
    }
}
