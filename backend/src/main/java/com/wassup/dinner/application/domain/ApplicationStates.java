package com.wassup.dinner.application.domain;

import com.wassup.dinner.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 신청 상태 변경 + 변경 이력 기록을 한 곳에서 처리한다. */
@Component
@RequiredArgsConstructor
public class ApplicationStates {

    public static final String ENTITY = "Application";

    private final AuditService audit;

    public void app(Application a, ApplicationStatus to, String reason, String actor) {
        audit.record(ENTITY, a.getId(), "appStatus", a.getAppStatus(), to, reason, actor);
        a.setAppStatus(to);
    }

    public void payment(Application a, PaymentStatus to, String reason, String actor) {
        audit.record(ENTITY, a.getId(), "paymentStatus", a.getPaymentStatus(), to, reason, actor);
        a.setPaymentStatus(to);
    }

    public void match(Application a, MatchStatus to, String reason, String actor) {
        audit.record(ENTITY, a.getId(), "matchStatus", a.getMatchStatus(), to, reason, actor);
        a.setMatchStatus(to);
        if (reason != null) {
            a.setStatusReason(reason);
        }
    }

    public void attendance(Application a, AttendanceStatus to, String reason, String actor) {
        audit.record(ENTITY, a.getId(), "attendanceStatus", a.getAttendanceStatus(), to, reason, actor);
        a.setAttendanceStatus(to);
    }
}
