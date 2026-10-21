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

@Tag(name = "Me - Profile", description = "기본 정보·매칭 프로필·동의")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final ProfileService profileService;

    public record ProfileRequest(
            @NotBlank String name,
            @NotNull @Min(1960) @Max(2008) Integer birthYear,
            @NotNull Gender gender,
            @NotBlank String job,
            @NotBlank String instagramId,
            @NotBlank @Pattern(regexp = "^[EI][SN][TF][JP]$", message = "MBTI 4글자 (예: ENFP)") String mbti,
            @NotEmpty Set<Interest> interests,
            @NotEmpty Set<Trait> traits,
            @NotEmpty Set<DesiredType> desiredTypes,
            @NotEmpty Set<PriceRange> priceRanges,
            String foodRestrictions) {
    }

    public record ConsentRequest(@AssertTrue(message = "개인정보 수집·이용 및 오픈 단톡방 동의가 필요합니다.") boolean privacy,
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

    @Operation(summary = "개인정보 수집·이용(오픈 단톡방 포함) 및 주의 사항 동의")
    @PostMapping("/consents")
    public ProfileResponse consent(LoginUser user, @Valid @RequestBody ConsentRequest request) {
        return profileService.consent(user.userId());
    }
}
