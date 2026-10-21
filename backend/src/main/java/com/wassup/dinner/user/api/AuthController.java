package com.wassup.dinner.user.api;

import com.wassup.dinner.user.application.AuthService;
import com.wassup.dinner.user.application.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "참가자/운영자 로그인 (경로 분리)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final com.wassup.dinner.user.application.OAuthService oAuthService;
    private final AuthService authService;

    /** 인증번호를 응답에 담을지 (문자 발송 수단이 없는 해커톤에서는 true) */
    @org.springframework.beans.factory.annotation.Value("${app.auth.expose-otp:true}")
    private boolean exposeOtp;

    public record ParticipantLoginRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "숫자만 입력 (예: 01012345678)") String phone,
            String name,
            String password) {
    }

    public record OtpRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "숫자만 입력 (예: 01012345678)") String phone,
            @jakarta.validation.constraints.NotNull OtpService.Purpose purpose) {
    }

    /** devCode: 문자 발송 수단이 없는 해커톤용으로 발급한 번호를 그대로 돌려준다 */
    public record OtpResponse(int expiresInSeconds, String devCode) {
    }

    public record SignupRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "숫자만 입력 (예: 01012345678)") String phone,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "인증번호 6자리") String code,
            @NotBlank String password,
            String name) {
    }

    public record ResetRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "숫자만 입력 (예: 01012345678)") String phone,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "인증번호 6자리") String code,
            @NotBlank String password) {
    }

    public record AdminLoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    public record LoginResponse(String token, String role, Long id, String name,
                                boolean profileComplete, boolean consented, String homePath) {
    }

    @Operation(summary = "참가자 로그인 (해커톤: 휴대폰 번호 테스트 로그인, 없으면 자동 가입)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody ParticipantLoginRequest request) {
        return authService.participantLogin(request.phone(), request.name(), request.password());
    }

    @Operation(summary = "휴대폰 인증번호 발급 (SIGNUP / RESET). 해커톤: 응답 devCode 로 번호를 알려준다")
    @PostMapping("/otp")
    public OtpResponse otp(@Valid @RequestBody OtpRequest request) {
        String code = authService.requestOtp(request.phone(), request.purpose());
        return new OtpResponse((int) OtpService.TTL.toSeconds(), exposeOtp ? code : null);
    }

    @Operation(summary = "회원가입: 인증번호 확인 + 비밀번호 설정 (8자 이상, 영문/숫자 포함)")
    @PostMapping("/signup")
    public LoginResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.phone(), request.code(), request.password(), request.name());
    }

    @Operation(summary = "비밀번호 재설정: 인증번호 확인 + 새 비밀번호")
    @PostMapping("/password/reset")
    public void reset(@Valid @RequestBody ResetRequest request) {
        authService.resetPassword(request.phone(), request.code(), request.password());
    }

    @Operation(summary = "가입된 휴대폰 번호인지 확인 (로그인/회원가입 화면 분기용)")
    @GetMapping("/registered")
    public java.util.Map<String, Boolean> registered(@RequestParam String phone) {
        return java.util.Map.of("registered", authService.isRegistered(phone.replaceAll("[^0-9]", "")));
    }

    @Operation(summary = "운영자 로그인 (기본 계정 admin / admin1234)")
    @PostMapping("/admin/login")
    public LoginResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return authService.adminLogin(request.loginId(), request.password());
    }

    public record OAuthLoginRequest(@NotBlank String code, String state) {
    }

    @Operation(summary = "소셜 로그인 이동 주소 (provider=kakao|naver, 키 미설정이면 configured=false)")
    @GetMapping("/oauth/{provider}/url")
    public com.wassup.dinner.user.application.OAuthService.AuthorizeUrl oauthUrl(@PathVariable String provider, @RequestParam(required = false) String state) {
        return oAuthService.authorizeUrl(com.wassup.dinner.user.application.OAuthService.Provider.valueOf(provider.toUpperCase()), state == null ? "" : state);
    }

    @Operation(summary = "소셜 로그인 완료 (인가 코드 → 로그인/자동 가입)")
    @PostMapping("/oauth/{provider}")
    public LoginResponse oauthLogin(@PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
        return oAuthService.login(com.wassup.dinner.user.application.OAuthService.Provider.valueOf(provider.toUpperCase()), request.code(), request.state());
    }
}
