package com.wassup.dinner.user.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 휴대폰 인증번호 (회원가입·비밀번호 재설정).
 * 해커톤에는 문자 발송 수단이 없어 6자리 번호를 서버에 3분간 보관하고, 발급 응답과 로그로 알려준다.
 * 번호당 마지막 발급분만 유효하고, 5회 틀리면 무효가 된다. 확인에 성공하면 바로 소모된다.
 */
@Slf4j
@Service
public class OtpService {

    public enum Purpose { SIGNUP, RESET }

    public static final Duration TTL = Duration.ofMinutes(3);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(String code, Purpose purpose, Instant expiresAt, int attempts) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** 새 인증번호 발급 (이전 번호는 무효). 발급한 번호를 돌려준다 */
    public String issue(String phone, Purpose purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        entries.put(phone, new Entry(code, purpose, Instant.now().plus(TTL), 0));
        log.info("[인증번호] {} {} → {}", purpose, mask(phone), code);
        return code;
    }

    /** 인증번호 확인 후 소모. 틀리거나 만료되면 예외 */
    public void consume(String phone, Purpose purpose, String code) {
        Entry e = entries.get(phone);
        if (e == null || e.purpose() != purpose || Instant.now().isAfter(e.expiresAt())) {
            entries.remove(phone);
            throw new ApiException(ErrorCode.INVALID_REQUEST, "인증번호가 만료됐어요. 다시 받아 주세요.");
        }
        if (!e.code().equals(code)) {
            if (e.attempts() + 1 >= MAX_ATTEMPTS) {
                entries.remove(phone);
                throw new ApiException(ErrorCode.INVALID_REQUEST, "인증번호를 여러 번 틀렸어요. 다시 받아 주세요.");
            }
            entries.put(phone, new Entry(e.code(), e.purpose(), e.expiresAt(), e.attempts() + 1));
            throw new ApiException(ErrorCode.INVALID_REQUEST, "인증번호가 맞지 않아요.");
        }
        entries.remove(phone);
    }

    private static String mask(String phone) {
        return phone.length() < 8 ? phone : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
