package com.wassup.dinner.application.api;

import com.wassup.dinner.application.application.ApplicationService;
import com.wassup.dinner.application.application.ApplicationService.SubmitRequest;
import com.wassup.dinner.application.application.ApplicationService.SubmitResponse;
import com.wassup.dinner.application.application.ApplicationView;
import com.wassup.dinner.application.domain.MatchResolution;
import com.wassup.dinner.common.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Applications", description = "참가 신청·상태 확인·취소·매칭 실패 해결")
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    public record ResolutionRequest(@NotNull MatchResolution.Choice choice, Long slotId) {
    }

    @Operation(summary = "신청 제출 (날짜·지역 복수 선택 + 참가권 or 보유 이용권)",
            description = "product 로 새 결제를 만들면 응답의 paymentId 로 POST /api/payments/{id}/confirm 을 호출한다.")
    @PostMapping
    public SubmitResponse submit(LoginUser user, @RequestBody SubmitRequest request) {
        return applicationService.submit(user.userId(), request);
    }

    @Operation(summary = "내 신청 목록 (홈 화면 상태 카드)")
    @GetMapping
    public List<ApplicationView> mine(LoginUser user) {
        return applicationService.mine(user.userId());
    }

    @Operation(summary = "신청 상세 (상태 타임라인·확정/재배치/실패 사유 포함)")
    @GetMapping("/{id}")
    public ApplicationView detail(LoginUser user, @PathVariable Long id) {
        return applicationService.detail(user.userId(), id);
    }

    @Operation(summary = "신청 취소 (환불 규정 자동 적용, 당일 취소 불가)")
    @PostMapping("/{id}/cancel")
    public ApplicationView cancel(LoginUser user, @PathVariable Long id) {
        return applicationService.cancel(user.userId(), id);
    }

    @Operation(summary = "매칭 실패 해결 선택: TRANSFER(slotId 필수) / KEEP_TICKET / REFUND")
    @PostMapping("/{id}/resolution")
    public ApplicationView resolve(LoginUser user, @PathVariable Long id, @Validated @RequestBody ResolutionRequest request) {
        return applicationService.resolve(user.userId(), id, request.choice(), request.slotId());
    }
}
