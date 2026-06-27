package com.mymall.common.exception;

import com.mymall.common.result.R;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一响应策略：HTTP 状态码始终返回 200，业务状态通过 {@link R#getCode()} 表达。
 * 理由：电商系统业务错误种类多，4xx/5xx 会被网关/CDN 拦截导致前端拿不到响应体；
 * 统一 200 + 业务码是国内主流电商的通行做法。详见 controller-specification.md §2。
 *
 * <p>处理优先级（从具体到通用）：
 * <ol>
 *   <li>BizException - 业务异常（code 由业务定义）</li>
 *   <li>参数校验/绑定异常 - R.code = 400</li>
 *   <li>Spring MVC 路由异常 - R.code = 404/405</li>
 *   <li>兜底 Exception - R.code = 500，不向客户端泄露堆栈</li>
 * </ol>
 */
@RestControllerAdvice(basePackages = "com.mymall")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 1. 业务异常 ====================

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    // ==================== 2. 参数校验/绑定异常（统一 R.code = 400） ====================

    /** @RequestBody 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return R.error(ResultCode.PARAM_ERROR, message);
    }

    /** @ModelAttribute / 表单参数校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return R.error(ResultCode.PARAM_ERROR, message);
    }

    /** @PathVariable / @RequestParam 上的 @Validated 约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", message);
        return R.error(ResultCode.PARAM_ERROR, message);
    }

    /** 缺少必填的 @RequestParam */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return R.error(ResultCode.PARAM_ERROR, "缺少参数: " + e.getParameterName());
    }

    /** 参数类型转换失败（如 ?id=abc 期望 Long） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={}, value={}", e.getName(), e.getValue());
        return R.error(ResultCode.PARAM_ERROR, "参数类型不匹配: " + e.getName());
    }

    /** 请求体 JSON 解析失败（格式错误 / 缺 body） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.error(ResultCode.PARAM_ERROR, "请求体格式错误或为空");
    }

    /** 上传文件超过限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return R.error(ResultCode.PARAM_ERROR, "上传文件超过大小限制");
    }

    // ==================== 3. Spring MVC 路由异常（R.code = 404/405） ====================

    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return R.error(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotAllowed(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return R.error(ResultCode.METHOD_NOT_ALLOWED,
                ResultCode.METHOD_NOT_ALLOWED.getMessage() + ": " + e.getMethod());
    }

    // ==================== 4. 兜底异常（R.code = 500，不泄露堆栈） ====================

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return R.error(ResultCode.INTERNAL_ERROR);
    }
}
