package com.mymall.common.util;

import java.util.Collections;
import java.util.List;

/**
 * 登录用户身份信息
 *
 * <p>由网关鉴权后通过 {@code X-User-Id} / {@code X-User-Name} / {@code X-User-Roles} 请求头透传，
 * 业务服务通过 {@link UserContextFilter} 解析组装为本对象，写入 {@link UserContext}。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code userId} - 用户 ID（memberId / adminId），必填</li>
 *   <li>{@code username} - 用户名，可能为 null（仅携带 ID 的简化令牌场景）</li>
 *   <li>{@code roles} - 角色列表，可能为空 List（匿名或未分配角色）</li>
 * </ul>
 *
 * <p>本类为不可变 record，{@code roles} 在构造时做防御性拷贝并返回不可修改视图。
 *
 * @param userId   用户 ID
 * @param username 用户名
 * @param roles    角色列表（不可为 null，构造时已兜底为空 List）
 */
public record UserInfo(Long userId, String username, List<String> roles) {

    public UserInfo {
        if (roles == null || roles.isEmpty()) {
            roles = Collections.emptyList();
        } else {
            roles = List.copyOf(roles);
        }
    }

    /**
     * 是否拥有指定角色
     *
     * @param role 角色名（如 {@code ROLE_ADMIN}），null 返回 false
     */
    public boolean hasRole(String role) {
        if (role == null || roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.contains(role);
    }
}
