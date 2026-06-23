package com.mymall.oss.config;

import com.mymall.common.util.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 用户身份上下文过滤器
 *
 * <p>从网关透传的 {@code X-User-Id} 请求头解析登录用户 ID，写入 {@link UserContext}，
 * 供 Service 层获取上传者身份（如 {@code oss_file_meta.uploader_id}）。
 * 请求结束后清理 ThreadLocal，防止线程池复用导致身份串号。
 *
 * <p>网关侧的 JWT 解析与 {@code X-User-Id} 注入由 mall-auth / 网关鉴权过滤器负责，
 * 待认证授权模块落地后对接；在此之前未登录请求 userId 为 null（不影响匿名上传场景）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    /** 网关透传用户 ID 的请求头名 */
    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                UserContext.setUserId(Long.parseLong(userIdStr));
            } catch (NumberFormatException e) {
                logger.warn("非法的 X-User-Id 请求头: " + userIdStr);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
