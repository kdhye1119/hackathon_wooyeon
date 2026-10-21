package com.wassup.dinner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SwaggerSmokeTest {

    @LocalServerPort int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 참가자_운영자_API_문서가_분리되어_생성된다() throws Exception {
        assertThat(get("/swagger-ui.html").statusCode()).isEqualTo(200);

        HttpResponse<String> participant = get("/v3/api-docs/1.%20participant");
        assertThat(participant.statusCode()).isEqualTo(200);
        assertThat(participant.body()).contains("/api/applications").doesNotContain("/api/admin/");
        // 토큰에서 주입되는 LoginUser 는 문서 파라미터로 노출되지 않는다
        assertThat(participant.body()).doesNotContain("\"name\":\"user\"").doesNotContain("loginUser");

        HttpResponse<String> admin = get("/v3/api-docs/2.%20admin");
        assertThat(admin.statusCode()).isEqualTo(200);
        assertThat(admin.body()).contains("/api/admin/dashboard").contains("/api/admin/match-runs");
    }

    @Test
    void 운영자_API는_참가자_토큰이나_무토큰으로_접근할_수_없다() throws Exception {
        assertThat(get("/api/admin/dashboard").statusCode()).isEqualTo(401);
        assertThat(get("/api/meta/options").statusCode()).isEqualTo(200);
    }
}
