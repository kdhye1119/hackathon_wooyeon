package com.wassup.dinner.user.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.user.api.AuthController.LoginResponse;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * 카카오·네이버 소셜 로그인 (ACC-01). 앱 키는 환경 설정으로 넣는다:
 * app.oauth.kakao.client-id / client-secret, app.oauth.naver.client-id / client-secret, app.oauth.redirect-base
 * 키가 없으면 소셜 로그인 버튼은 '설정 필요' 안내만 보여준다.
 */
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final Environment env;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AppClock clock;
    private final RestClient http = RestClient.create();

    public enum Provider {
        KAKAO("https://kauth.kakao.com/oauth/authorize", "https://kauth.kakao.com/oauth/token", "https://kapi.kakao.com/v2/user/me"),
        NAVER("https://nid.naver.com/oauth2.0/authorize", "https://nid.naver.com/oauth2.0/token", "https://openapi.naver.com/v1/nid/me");

        final String authorizeUrl;
        final String tokenUrl;
        final String profileUrl;

        Provider(String authorizeUrl, String tokenUrl, String profileUrl) {
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.profileUrl = profileUrl;
        }

        String key() {
            return name().toLowerCase();
        }
    }

    public record AuthorizeUrl(boolean configured, String url) {
    }

    private String clientId(Provider p) {
        return env.getProperty("app.oauth." + p.key() + ".client-id", "");
    }

    private String secret(Provider p) {
        return env.getProperty("app.oauth." + p.key() + ".client-secret", "");
    }

    private String redirectUri(Provider p) {
        return env.getProperty("app.oauth.redirect-base", "http://localhost:5173") + "/oauth/" + p.key();
    }

    /** 로그인 화면 이동 주소 (키가 없으면 configured=false) */
    public AuthorizeUrl authorizeUrl(Provider p, String state) {
        if (clientId(p).isBlank()) {
            return new AuthorizeUrl(false, null);
        }
        String url = UriComponentsBuilder.fromUriString(p.authorizeUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId(p))
                .queryParam("redirect_uri", redirectUri(p))
                .queryParam("state", state)
                .build().toUriString();
        return new AuthorizeUrl(true, url);
    }

    /** 인가 코드로 토큰·프로필을 받아 로그인 (처음이면 가입) */
    @Transactional
    @SuppressWarnings("unchecked")
    public LoginResponse login(Provider p, String code, String state) {
        if (clientId(p).isBlank()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "소셜 로그인 키가 설정되지 않았어요.");
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId(p));
            if (!secret(p).isBlank()) form.add("client_secret", secret(p));
            form.add("redirect_uri", redirectUri(p));
            form.add("code", code);
            if (state != null) form.add("state", state);
            Map<String, Object> token = http.post().uri(p.tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            String accessToken = token == null ? null : (String) token.get("access_token");
            if (accessToken == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "소셜 로그인에 실패했어요.");
            }
            Map<String, Object> me = http.get().uri(p.profileUrl).header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(Map.class);
            String socialId;
            String name;
            if (p == Provider.KAKAO) {
                socialId = String.valueOf(me.get("id"));
                Map<String, Object> props = (Map<String, Object>) me.getOrDefault("properties", Map.of());
                name = (String) props.get("nickname");
            } else {
                Map<String, Object> res = (Map<String, Object>) me.getOrDefault("response", Map.of());
                socialId = String.valueOf(res.get("id"));
                name = (String) res.getOrDefault("name", res.get("nickname"));
            }
            String key = p.key() + ":" + socialId;
            User user = userRepository.findBySocialKey(key).orElseGet(() -> {
                // 휴대폰 번호는 가입 뒤 '연락처 변경'으로 인증해 등록한다 (그전까지 고유한 임시 값)
                User u = new User("S" + UUID.randomUUID().toString().replace("-", "").substring(0, 19), name, clock.now());
                u.setSocialKey(key);
                return userRepository.save(u);
            });
            return authService.loginResponse(user);
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "소셜 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.");
        }
    }
}
