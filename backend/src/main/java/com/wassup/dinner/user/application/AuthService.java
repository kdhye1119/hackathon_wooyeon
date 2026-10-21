package com.wassup.dinner.user.application;

import com.wassup.dinner.common.auth.PasswordHasher;
import com.wassup.dinner.common.auth.Role;
import com.wassup.dinner.common.auth.TokenProvider;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.user.api.AuthController.LoginResponse;
import com.wassup.dinner.user.domain.AdminAccount;
import com.wassup.dinner.user.domain.AdminAccountRepository;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final TokenProvider tokenProvider;
    private final PasswordHasher passwordHasher;
    private final AppClock clock;
    private final OtpService otpService;

    @Transactional
    /**
     * 참가자 로그인.
     * - 비밀번호를 설정한 계정: 비밀번호가 맞아야 로그인
     * - 비밀번호가 없는 계정(시연·가상 데이터): 휴대폰 번호만으로 테스트 로그인 (ACC-01 해커톤 허용)
     * - 없는 번호: 비밀번호 없이 들어오면 테스트 계정으로 자동 가입, 비밀번호를 보내면 가입 안내
     */
    public LoginResponse participantLogin(String phone, String name, String password) {
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            if (password != null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "가입되지 않은 번호예요. 회원가입을 진행해 주세요.");
            }
            user = userRepository.save(new User(phone, name, clock.now()));
        } else if (user.hasPassword() && (password == null || !passwordHasher.matches(password, user.getPasswordHash()))) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "비밀번호가 올바르지 않아요.");
        }
        if (user.getName() == null && name != null) {
            user.setName(name);
        }
        return toLogin(user);
    }

    /** 인증번호 발급. 회원가입은 비밀번호가 있는 가입 번호면, 재설정은 없는 번호면 거절 */
    @Transactional(readOnly = true)
    public String requestOtp(String phone, OtpService.Purpose purpose) {
        User user = userRepository.findByPhone(phone).orElse(null);
        if (purpose == OtpService.Purpose.SIGNUP && user != null && user.hasPassword()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 가입된 번호예요. 로그인해 주세요.");
        }
        if (purpose == OtpService.Purpose.RESET && user == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "가입되지 않은 번호예요.");
        }
        if (purpose == OtpService.Purpose.CHANGE_PHONE && user != null) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 다른 계정에서 쓰는 번호예요.");
        }
        return otpService.issue(phone, purpose);
    }

    /** 연락처 변경: 새 번호로 받은 인증번호를 확인한 뒤 바꾼다 */
    @Transactional
    public void changePhone(Long userId, String phone, String code) {
        if (userRepository.findByPhone(phone).isPresent()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 다른 계정에서 쓰는 번호예요.");
        }
        otpService.consume(phone, OtpService.Purpose.CHANGE_PHONE, code);
        User u = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
        u.setPhone(phone);
    }

    /** 회원가입: 인증번호 확인 + 비밀번호 설정. 비밀번호 없이 쓰던 테스트 계정이면 비밀번호만 설정 */
    @Transactional
    public LoginResponse signup(String phone, String code, String password, String name) {
        checkPolicy(password);
        User existing = userRepository.findByPhone(phone).orElse(null);
        if (existing != null && existing.hasPassword()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 가입된 번호예요. 로그인해 주세요.");
        }
        otpService.consume(phone, OtpService.Purpose.SIGNUP, code);
        User user = existing != null ? existing : userRepository.save(new User(phone, name, clock.now()));
        user.setPasswordHash(passwordHasher.hash(password));
        return toLogin(user);
    }

    /** 비밀번호 재설정: 인증번호 확인 후 새 비밀번호 */
    @Transactional
    public void resetPassword(String phone, String code, String newPassword) {
        checkPolicy(newPassword);
        User user = userRepository.findByPhone(phone).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "가입되지 않은 번호예요."));
        otpService.consume(phone, OtpService.Purpose.RESET, code);
        user.setPasswordHash(passwordHasher.hash(newPassword));
    }

    /** 8자 이상, 영문과 숫자 모두 포함 (회원가입 화면 1-7 규칙과 같음) */
    static void checkPolicy(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "비밀번호는 8자 이상, 영문과 숫자를 모두 포함해야 해요.");
        }
    }

    public LoginResponse loginResponse(User user) {
        return toLogin(user);
    }

    private LoginResponse toLogin(User user) {
        return new LoginResponse(tokenProvider.issue(user.getId(), Role.USER), Role.USER.name(), user.getId(),
                user.getName(), user.isProfileComplete(), user.hasConsented(), "/");
    }

    @Transactional(readOnly = true)
    public boolean isRegistered(String phone) {
        return userRepository.findByPhone(phone).isPresent();
    }

    @Transactional(readOnly = true)
    public LoginResponse adminLogin(String loginId, String password) {
        AdminAccount admin = adminAccountRepository.findByLoginId(loginId)
                .filter(a -> passwordHasher.matches(password, a.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));
        return new LoginResponse(tokenProvider.issue(admin.getId(), Role.ADMIN), Role.ADMIN.name(), admin.getId(),
                admin.getName(), true, true, "/admin");
    }
}
