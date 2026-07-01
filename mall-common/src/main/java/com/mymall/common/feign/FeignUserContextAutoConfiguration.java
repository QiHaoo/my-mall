package com.mymall.common.feign;

import com.mymall.common.util.UserContext;
import com.mymall.common.util.UserInfo;
import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Feign 用户上下文透传自动配置
 *
 * <p>注册 {@link RequestInterceptor} Bean，在每次 Feign 调用前往请求头写入
 * {@code X-User-Id} / {@code X-User-Name} / {@code X-User-Roles}，下游服务通过
 * {@link com.mymall.common.web.UserContextFilter} 解析，使服务间调用也能拿到调用者身份。
 *
 * <p><b>生效条件</b>：类路径存在 {@link RequestInterceptor}（即业务模块依赖
 * {@code spring-cloud-starter-openfeign}）。mall-common 已传递 OpenFeign 依赖，
 * 所有业务模块自动生效，无需手动注册。
 *
 * <p><b>异步线程池注意</b>：{@link UserContext} 基于普通 ThreadLocal，{@code @Async} 或
 * 自定义线程池中不会自动传递。异步 Feign 调用需在主线程取出 {@link UserInfo} 后作为
 * 显式参数传递，或在异步任务中手动 {@link UserContext#set(UserInfo)} 后再调用。
 *
 * <p><b>信任边界</b>：Feign 调用不经过网关，下游服务直接信任 {@code X-User-*} 头。须确保
 * 服务间网络隔离（K8s NetworkPolicy），防止外部直接访问业务服务伪造身份。
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignUserContextAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FeignUserContextAutoConfiguration.class);

    /** 网关透传用户 ID 的请求头名（与 UserContextFilter 保持一致） */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** 网关透传用户名的请求头名 */
    public static final String USER_NAME_HEADER = "X-User-Name";

    /** 网关透传角色列表的请求头名 */
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    @Bean
    public RequestInterceptor feignUserContextInterceptor() {
        return template -> {
            UserInfo user = UserContext.get();
            if (user == null) {
                // 当前线程无登录上下文（如定时任务发起的 Feign 调用），不透传
                if (log.isDebugEnabled()) {
                    log.debug("Feign 调用未携带用户上下文: url={}", template.url());
                }
                return;
            }
            template.header(USER_ID_HEADER, String.valueOf(user.userId()));
            if (user.username() != null) {
                template.header(USER_NAME_HEADER, user.username());
            }
            if (user.roles() != null && !user.roles().isEmpty()) {
                template.header(USER_ROLES_HEADER, String.join(",", user.roles()));
            }
        };
    }
}
