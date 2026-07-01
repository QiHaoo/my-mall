package com.mymall.common.web;

import com.mymall.common.util.UserContext;
import com.mymall.common.util.UserInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 用户身份上下文过滤器
 *
 * <p>从网关透传的 {@code X-User-Id} / {@code X-User-Name} / {@code X-User-Roles} 请求头
 * 解析登录用户身份，组装 {@link UserInfo} 写入 {@link UserContext}，供 Service 层获取
 * 上传者身份（如 {@code oss_file_meta.uploader_id}、订单归属校验等）。请求结束后清理
 * ThreadLocal，防止线程池复用导致身份串号。
 *
 * <p><b>信任边界</b>：业务服务信任网关透传的请求头，不二次校验 JWT 签名。生产环境须确保：
 * <ol>
 *   <li>网关在转发前覆盖客户端传入的 {@code X-User-*} 头（即使客户端伪造也无效）</li>
 *   <li>服务间网络隔离（K8s NetworkPolicy），外部无法直接访问业务服务</li>
 * </ol>
 *
 * <p><b>当前阶段</b>：网关鉴权过滤器尚未落地，未登录请求 userId 为 null（不影响匿名场景
 * 如 OSS 直传）。认证授权模块落地后，{@code X-User-*} 头由网关注入，本过滤器无需改动。
 *
 * <p><b>解析规则</b>：
 * <ul>
 *   <li>{@code X-User-Id} 必填，缺失或格式非法时 userId 为 null（不抛异常，业务自行决定是否拒绝）</li>
 *   <li>{@code X-User-Name} 可选，缺失时为 null</li>
 *   <li>{@code X-User-Roles} 可选，逗号分隔（如 {@code ROLE_ADMIN,ROLE_USER}），缺失时为空 List</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    /** 网关透传用户 ID 的请求头名 */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** 网关透传用户名的请求头名 */
    public static final String USER_NAME_HEADER = "X-User-Name";

    /** 网关透传角色列表的请求头名（逗号分隔） */
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UserInfo userInfo = parseUserInfo(request);
        if (userInfo != null) {
            UserContext.set(userInfo);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private UserInfo parseUserInfo(HttpServletRequest request) {
        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr == null || userIdStr.isBlank()) {
            // 未携带 X-User-Id 视为匿名请求，不写入上下文
            return null;
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            log.warn("非法的 X-User-Id 请求头: {}", userIdStr);
            return null;
        }

        String username = request.getHeader(USER_NAME_HEADER);
        List<String> roles = parseRoles(request.getHeader(USER_ROLES_HEADER));
        return new UserInfo(userId, username, roles);
    }

    private List<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
