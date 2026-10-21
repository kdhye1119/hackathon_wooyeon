package com.wassup.dinner.common.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * HMAC-SHA256 서명 토큰. 형식: base64url(userId:role:expEpoch).base64url(signature)
 * 해커톤용 경량 구현이며, 실서비스에서는 JWT 라이브러리 + 리프레시 토큰으로 교체한다.
 */
@Component
@RequiredArgsConstructor
public class TokenProvider {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final AuthProperties properties;

    public String issue(Long userId, Role role) {
        long exp = Instant.now().plusSeconds(properties.tokenTtlHours() * 3600).getEpochSecond();
        String payload = userId + ":" + role.name() + ":" + exp;
        String body = ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + ENC.encodeToString(sign(body));
    }

    public Optional<LoginUser> parse(String token) {
        try {
            if (token == null) {
                return Optional.empty();
            }
            String raw = token.startsWith("Bearer ") ? token.substring(7) : token;
            int dot = raw.indexOf('.');
            if (dot < 0) {
                return Optional.empty();
            }
            String body = raw.substring(0, dot);
            byte[] sig = DEC.decode(raw.substring(dot + 1));
            if (!MessageDigest.isEqual(sig, sign(body))) {
                return Optional.empty();
            }
            String[] parts = new String(DEC.decode(body), StandardCharsets.UTF_8).split(":");
            if (Long.parseLong(parts[2]) < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new LoginUser(Long.valueOf(parts[0]), Role.valueOf(parts[1])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.tokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("토큰 서명 실패", e);
        }
    }
}
