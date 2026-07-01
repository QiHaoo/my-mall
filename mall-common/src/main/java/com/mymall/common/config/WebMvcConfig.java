package com.mymall.common.config;

import com.mymall.common.web.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 扩展配置
 *
 * <p>注册自定义 {@link HandlerMethodArgumentResolver}。当前仅 {@link CurrentUserArgumentResolver}，
 * 用于解析 {@code @CurrentUser} 标注的方法参数（当前登录用户）。
 *
 * <p>本类通过 {@link MybatisPlusConfig} 上的 {@code @ComponentScan("com.mymall.common")}
 * 自动扫描生效，业务模块无需额外配置。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
