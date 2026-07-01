package com.mymall.common.web;

import com.mymall.common.util.UserInfo;
import com.mymall.common.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CurrentUserArgumentResolver} 单元测试
 * <p>
 * 验证 {@link CurrentUser} 参数解析的两种类型（Long / UserInfo）与未登录场景的兜底行为。
 */
@DisplayName("CurrentUserArgumentResolver")
class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    /** 用作反射获取参数类型的载体方法 */
    @SuppressWarnings("unused")
    private void sampleMethod(@CurrentUser Long userId,
                              @CurrentUser UserInfo user,
                              @CurrentUser long primitiveUserId,
                              Long notAnnotated,
                              @CurrentUser String unsupported) {
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        return MethodParameter.forExecutable(
                CurrentUserArgumentResolverTest.class.getDeclaredMethod(
                        "sampleMethod", Long.class, UserInfo.class, long.class, Long.class, String.class),
                index);
    }

    private NativeWebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }

    // ==================== supportsParameter ====================

    @Nested
    @DisplayName("supportsParameter")
    class SupportsParameter {

        @Test
        @DisplayName("@CurrentUser Long 应支持")
        void supportsAnnotatedLong() throws NoSuchMethodException {
            assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        }

        @Test
        @DisplayName("@CurrentUser UserInfo 应支持")
        void supportsAnnotatedUserInfo() throws NoSuchMethodException {
            assertThat(resolver.supportsParameter(parameter(1))).isTrue();
        }

        @Test
        @DisplayName("@CurrentUser long 基础类型应支持")
        void supportsAnnotatedPrimitiveLong() throws NoSuchMethodException {
            assertThat(resolver.supportsParameter(parameter(2))).isTrue();
        }

        @Test
        @DisplayName("无 @CurrentUser 注解的参数应不支持")
        void rejectsNotAnnotated() throws NoSuchMethodException {
            assertThat(resolver.supportsParameter(parameter(3))).isFalse();
        }

        @Test
        @DisplayName("@CurrentUser 标在不支持的类型上应不支持")
        void rejectsUnsupportedType() throws NoSuchMethodException {
            assertThat(resolver.supportsParameter(parameter(4))).isFalse();
        }
    }

    // ==================== resolveArgument ====================

    @Nested
    @DisplayName("resolveArgument")
    class ResolveArgument {

        @Test
        @DisplayName("已登录 - Long 参数返回 userId")
        void resolvesLongWhenLoggedIn() throws Exception {
            // Given
            UserContext.set(new UserInfo(1001L, "alice", List.of("ROLE_USER")));

            // When
            Object result = resolver.resolveArgument(parameter(0), null, webRequest(), null);

            // Then
            assertThat(result).isEqualTo(1001L);
        }

        @Test
        @DisplayName("已登录 - UserInfo 参数返回完整对象")
        void resolvesUserInfoWhenLoggedIn() throws Exception {
            // Given
            UserContext.set(new UserInfo(1002L, "bob", List.of("ROLE_ADMIN", "ROLE_OPERATOR")));

            // When
            Object result = resolver.resolveArgument(parameter(1), null, webRequest(), null);

            // Then
            assertThat(result).isInstanceOf(UserInfo.class);
            UserInfo info = (UserInfo) result;
            assertThat(info.userId()).isEqualTo(1002L);
            assertThat(info.username()).isEqualTo("bob");
            assertThat(info.roles()).containsExactly("ROLE_ADMIN", "ROLE_OPERATOR");
        }

        @Test
        @DisplayName("已登录 - long 基础类型返回 userId")
        void resolvesPrimitiveLongWhenLoggedIn() throws Exception {
            // Given
            UserContext.set(new UserInfo(1003L, "carol", List.of()));

            // When
            Object result = resolver.resolveArgument(parameter(2), null, webRequest(), null);

            // Then
            assertThat(result).isEqualTo(1003L);
        }

        @Test
        @DisplayName("未登录 - Long 参数返回 null")
        void returnsNullWhenNotLoggedIn() throws Exception {
            // Given: UserContext 未设置

            // When
            Object result = resolver.resolveArgument(parameter(0), null, webRequest(), null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("未登录 - UserInfo 参数返回 null")
        void returnsNullUserInfoWhenNotLoggedIn() throws Exception {
            // Given: UserContext 未设置

            // When
            Object result = resolver.resolveArgument(parameter(1), null, webRequest(), null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("未登录 - long 基础类型返回 0（业务应主动校验）")
        void returnsZeroWhenNotLoggedInPrimitive() throws Exception {
            // Given: UserContext 未设置

            // When
            Object result = resolver.resolveArgument(parameter(2), null, webRequest(), null);

            // Then
            assertThat(result).isEqualTo(0L);
        }
    }
}
