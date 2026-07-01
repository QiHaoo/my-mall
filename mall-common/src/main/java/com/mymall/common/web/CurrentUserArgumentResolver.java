package com.mymall.common.web;

import com.mymall.common.util.UserInfo;
import com.mymall.common.util.UserContext;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 {@link CurrentUser} 标注的方法参数
 *
 * <p>从 {@link UserContext} 取当前登录用户，按参数类型返回：
 * <ul>
 *   <li>{@code Long}（或 {@code long}）参数：返回 {@code userId}，未登录返回 {@code null}</li>
 *   <li>{@link UserInfo} 参数：返回完整对象，未登录返回 {@code null}</li>
 *   <li>其他类型：抛 {@link IllegalArgumentException}（启动期不触发，仅参数写错时）</li>
 * </ul>
 *
 * <p>注册方式见 {@link com.mymall.common.config.WebMvcConfig}。
 *
 * @see CurrentUser
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return Long.class == type || long.class == type || UserInfo.class == type;
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        UserInfo user = UserContext.get();
        Class<?> type = parameter.getParameterType();
        if (user == null) {
            // 基础类型 long 不能返回 null，返回 0 表示未登录（业务应主动校验）
            if (long.class == type) {
                return 0L;
            }
            return null;
        }
        if (Long.class == type || long.class == type) {
            return user.userId();
        }
        return user;
    }
}
