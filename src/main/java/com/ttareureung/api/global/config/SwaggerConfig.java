package com.ttareureung.api.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("따르릉 API")
                        .description("AI 기반 시니어 복약 관리 서비스의 백엔드 API입니다.")
                        .version("v1.0.0"))
                // 기본값은 모든 API에 Bearer 토큰 전송. 토큰이 필요 없는 API는 Controller에서 @SecurityRequirements()로 제외
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 액세스 토큰을 입력하세요. Bearer 접두어는 자동으로 추가됩니다.")));
    }
}
