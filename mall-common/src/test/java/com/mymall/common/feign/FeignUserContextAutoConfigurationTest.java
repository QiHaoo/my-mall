package com.mymall.common.feign;

import com.mymall.common.util.UserContext;
import com.mymall.common.util.UserInfo;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FeignUserContextAutoConfiguration} 单元测试
 * <p>
 * 验证 Feign 拦截器在三种场景下的请求头写入行为：已登录（完整字段）、已登录（仅 userId）、未登录。
 */
@DisplayName("FeignUserContextInterceptor")
class FeignUserContextAutoConfigurationTest {

    private final RequestInterceptor interceptor =
            new FeignUserContextAutoConfiguration().feignUserContextInterceptor();

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private List<String> headers(RequestTemplate template, String name) {
        Collection<String> values = template.headers().get(name);
        return values == null ? List.of() : List.copyOf(values);
    }

    // ==================== 已登录场景 ====================

    @Nested
    @DisplayName("已登录")
    class LoggedIn {

        @Test
        @DisplayName("完整用户信息 - 透传三个头")
        void propagatesAllHeaders() {
            // Given
            UserContext.set(new UserInfo(1001L, "alice", List.of("ROLE_USER", "ROLE_OPERATOR")));

            // When
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            // Then
            assertThat(headers(template, "X-User-Id")).containsExactly("1001");
            assertThat(headers(template, "X-User-Name")).containsExactly("alice");
            assertThat(headers(template, "X-User-Roles")).containsExactly("ROLE_USER,ROLE_OPERATOR");
        }

        @Test
        @DisplayName("仅 userId（username/roles 为 null/空） - 只透传 X-User-Id")
        void propagatesOnlyUserIdWhenOthersMissing() {
            // Given
            UserContext.set(new UserInfo(1002L, null, List.of()));

            // When
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            // Then
            assertThat(headers(template, "X-User-Id")).containsExactly("1002");
            assertThat(headers(template, "X-User-Name")).isEmpty();
            assertThat(headers(template, "X-User-Roles")).isEmpty();
        }

        @Test
        @DisplayName("userId 为 null（异常情况） - 仍透传字符串 'null'")
        void propagatesNullUserIdAsString() {
            // Given: 异常构造（不应出现，但拦截器不应崩溃）
            UserContext.set(new UserInfo(null, "ghost", List.of("ROLE_USER")));

            // When
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            // Then: 拦截器不解读语义，仅做 String.valueOf 转换
            assertThat(headers(template, "X-User-Id")).containsExactly("null");
        }
    }

    // ==================== 未登录场景 ====================

    @Nested
    @DisplayName("未登录")
    class NotLoggedIn {

        @Test
        @DisplayName("UserContext 为空 - 不写入任何用户头")
        void noHeadersWhenContextEmpty() {
            // Given: UserContext 未设置

            // When
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            // Then
            assertThat(template.headers()).doesNotContainKey("X-User-Id");
            assertThat(template.headers()).doesNotContainKey("X-User-Name");
            assertThat(template.headers()).doesNotContainKey("X-User-Roles");
        }
    }
}
