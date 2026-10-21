package com.wassup.dinner.user.application;

import com.wassup.dinner.application.application.ApplicationService;
import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.application.domain.ApplicationStatus;
import com.wassup.dinner.application.domain.AttendanceStatus;
import com.wassup.dinner.application.domain.MatchStatus;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.AccountStatus;
import com.wassup.dinner.user.domain.Profile;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;

/**
 * 회원 탈퇴.
 * - 확정된 다가오는 모임이나 선택을 기다리는 매칭 실패 건이 있으면 먼저 정리하게 한다.
 * - 진행 중인 신청은 환불 규정대로 취소한다.
 * - 이름·연락처·인스타그램·직업·식사 조건 등 식별 정보는 즉시 지운다.
 * - 결제·환불 기록(전자상거래법 5년), 신고·차단 기록(안전 운영 1년)은 개인을 알아볼 수 없게 한 채 보관한다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawService {

    public static final String RETENTION_POLICY = "탈퇴하면 이름, 연락처, 인스타그램, 직업, 식사 조건은 바로 삭제돼요. "
            + "결제/환불 기록은 전자상거래법에 따라 5년, 신고/차단 기록은 안전한 운영을 위해 1년간 개인을 알아볼 수 없게 보관한 뒤 삭제해요.";

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final EventSlotRepository slotRepository;
    private final AppClock clock;

    @Transactional
    public void withdraw(Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
        if (u.getStatus() == AccountStatus.WITHDRAWN) {
            return;
        }
        LocalDateTime now = clock.now();
        for (Application a : applicationRepository.findByUserIdOrderByIdDesc(userId)) {
            if (a.getAppStatus() != ApplicationStatus.SUBMITTED) {
                continue;
            }
            if (a.getMatchStatus() == MatchStatus.CONFIRMED && a.getAttendanceStatus() == AttendanceStatus.SCHEDULED
                    && a.getAssignedSlotId() != null
                    && slotRepository.findById(a.getAssignedSlotId()).map(s -> s.startAt().isAfter(now)).orElse(false)) {
                throw new ApiException(ErrorCode.INVALID_STATE, "확정된 모임이 있어요. 모임이 끝난 뒤 탈퇴할 수 있어요.");
            }
            if (a.getMatchStatus() == MatchStatus.ALTERNATIVE_OFFERED) {
                throw new ApiException(ErrorCode.INVALID_STATE, "매칭 실패 건의 일정 이동/보관/환불을 먼저 선택해 주세요.");
            }
        }
        // 진행 중인 신청은 환불 규정대로 취소
        for (Application a : applicationRepository.findByUserIdOrderByIdDesc(userId)) {
            if (a.isActive()) {
                applicationService.cancel(userId, a.getId());
            }
        }
        // 식별 정보 삭제 (연령대/성별 등 통계용 값만 남긴다)
        u.setPhone("W" + u.getId());
        u.setName("탈퇴한 회원");
        u.setPasswordHash(null);
        u.setStatus(AccountStatus.WITHDRAWN);
        u.setPrivacyAgreedAt(null);
        u.setPolicyAgreedAt(null);
        Profile p = u.getProfile();
        p.setJob(null);
        p.setInstagramId(null);
        p.setFoodRestrictions(null);
        p.setInterests(new LinkedHashSet<>());
        p.setTraits(new LinkedHashSet<>());
        p.setDesiredTypes(new LinkedHashSet<>());
        p.setPriceRanges(new LinkedHashSet<>());
    }
}
