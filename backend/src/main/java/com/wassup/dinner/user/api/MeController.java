package com.wassup.dinner.user.api;

import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.user.application.ProfileService;
import com.wassup.dinner.user.domain.Options.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;

@Tag(name = "Me - Profile", description = "기본 정보/매칭 프로필/동의")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final com.wassup.dinner.user.application.AuthService authService;
    private final com.wassup.dinner.user.application.WithdrawService withdrawService;
    private final ProfileService profileService;

    public record ProfileRequest(
            @NotBlank String name,
            @NotNull @Min(1960) @Max(2008) Integer birthYear,
            @NotNull Gender gender,
            /* 선택 */ String job,
            /* 선택 */ String instagramId,
            /* 선택 (모름이면 비움) */ @Pattern(regexp = "^([EIei][SNsn][TFtf][JPjp])?$", message = "MBTI 4글자 (예: ENFP)") String mbti,
            /* 선택 */ Set<Interest> interests,
            /* 선택 */ Set<Trait> traits,
            /* 매칭 선호는 비워도 된다 (신청서에서 고르지 않으면 null/빈 값) */
            Set<DesiredType> desiredTypes,
            Set<PriceRange> priceRanges,
            String foodRestrictions) {
    }

    public record ConsentRequest(@AssertTrue(message = "개인정보 수집/이용 및 오픈 단톡방 동의가 필요합니다.") boolean privacy,
                                 @AssertTrue(message = "주의 사항 확인이 필요합니다.") boolean policy) {
    }

    public record ProfileResponse(Long userId, String phone, String name, String accountStatus,
                                  Integer birthYear, Gender gender, String job, String instagramId, String mbti,
                                  Set<String> interests, Set<String> traits, Set<String> desiredTypes,
                                  Set<String> priceRanges, String foodRestrictions,
                                  boolean profileComplete, LocalDateTime privacyAgreedAt, LocalDateTime policyAgreedAt) {
    }

    @Operation(summary = "내 프로필 조회 (재신청 시 기존 프로필 불러오기)")
    @GetMapping("/profile")
    public ProfileResponse profile(LoginUser user) {
        return profileService.get(user.userId());
    }

    @Operation(summary = "내 프로필 저장/수정")
    @PutMapping("/profile")
    public ProfileResponse update(LoginUser user, @Valid @RequestBody ProfileRequest request) {
        return profileService.update(user.userId(), request);
    }

    @Operation(summary = "개인정보 수집/이용(오픈 단톡방 포함) 및 주의 사항 동의")
    @PostMapping("/consents")
    public ProfileResponse consent(LoginUser user, @Valid @RequestBody ConsentRequest request) {
        return profileService.consent(user.userId());
    }

    public record WithdrawInfo(String retentionPolicy) {
    }

    @Operation(summary = "탈퇴 안내 (보관 정책)")
    @GetMapping("/withdraw")
    public WithdrawInfo withdrawInfo() {
        return new WithdrawInfo(com.wassup.dinner.user.application.WithdrawService.RETENTION_POLICY);
    }

    @Operation(summary = "회원 탈퇴: 진행 중 신청 취소(환불 규정) + 식별 정보 삭제")
    @PostMapping("/withdraw")
    public void withdraw(LoginUser user) {
        withdrawService.withdraw(user.userId());
    }

    public record PhoneChangeRequest(
            @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "숫자만 입력 (예: 01012345678)") String phone,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "인증번호 6자리") String code) {
    }

    @Operation(summary = "연락처 변경 (새 번호로 받은 인증번호 확인, 인증번호 요청은 POST /api/auth/otp purpose=CHANGE_PHONE)")
    @PostMapping("/phone")
    public ProfileResponse changePhone(LoginUser user, @Valid @RequestBody PhoneChangeRequest request) {
        authService.changePhone(user.userId(), request.phone(), request.code());
        return profileService.get(user.userId());
    }
}
