package com.wassup.dinner.common.config;

import com.wassup.dinner.common.auth.LoginUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    static {
        // 컨트롤러의 LoginUser 파라미터는 토큰에서 주입되므로 문서에서 숨긴다.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(LoginUser.class);
    }

    @Bean
    public OpenAPI dinnerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("우연한 식탁 API")
                        .description("""
                                참가자 앱 + 운영자 어드민 백엔드 API.

                                1. `POST /api/auth/test-login` 으로 토큰 발급 (운영자: 01000000000)
                                2. 우측 상단 Authorize 에 토큰 입력
                                3. 실시간 채팅·알림은 STOMP WebSocket `/ws` (CONNECT 헤더 Authorization: Bearer {token})
                                   - 구독: `/topic/rooms/{roomId}`, `/user/queue/notifications`
                                   - 발행: `/app/rooms/{roomId}/send`  {"content": "..."}
                                """)
                        .version("v0.1"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GroupedOpenApi participantApi() {
        return GroupedOpenApi.builder()
                .group("1. participant")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("2. admin")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
