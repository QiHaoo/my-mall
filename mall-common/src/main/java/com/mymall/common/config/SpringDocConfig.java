package com.mymall.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置
 * <p>
 * 提供 Swagger UI 基础配置，各服务引入 mall-common 后自动生效。
 * <p>
 * 访问地址：http://localhost:{port}/swagger-ui.html
 *
 * <p>JWT 鉴权：待 mall-auth 落地后，在此追加 securitySchemes（Bearer）与 securityRequirements，
 * 并在网关层完成 token 校验后透传用户身份。当前暂不启用。
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("my-mall API")
                        .version("v1")
                        .description("my-mall 商城微服务接口文档")
                        .contact(new Contact()
                                .name("my-mall")
                                .url("https://github.com/my-mall")));
    }
}
