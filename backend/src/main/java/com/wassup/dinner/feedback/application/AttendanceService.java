package com.wassup.dinner.feedback.application;

import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.slot.domain.SlotStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 체크인 → 참석 기록, 행사 종료 시 미체크인 노쇼 후보 처리, 피드백 요청·1회 리마인드. */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository slotRepository;
    private final DiningTableRepository tableRepository;
    private final FeedbackRepository feedbackRepository;
    private final ApplicationStates states;
    private final NotificationService notificationService;
    private final ExceptionCaseService exceptionCaseService;
    private final ChatService chatService;
    private final AppClock clock;

    /** 체크인 가능: 시작 1시간 전 ~ 종료 시각 */
    @Transactional
    public void checkIn(Long userId, Long applicationId) {
        Application a = applicationRepository.findById(applicationId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("신청"));
        if (!a.isSeated()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "확정된 테이블이 없습니다.");
        }
        if (a.getAttendanceStatus() == AttendanceStatus.ATTENDED) {
            return;
        }
        EventSlot slot = slotRepository.findById(a.getAssignedSlotId()).orElseThrow();
        LocalDateTime now = clock.now();
        if (now.isBefore(slot.startAt().minusHours(1)) || !now.isBefore(slot.endAt())) {
            throw new ApiException(ErrorCode.CHECKIN_NOT_ALLOWED,
                    "체크인은 " + slot.startAt().minusHours(1).toLocalTime() + " ~ " + slot.endAt().toLocalTime() + " 사이에 할 수 있어요.");
        }
        a.setCheckedInAt(now);
        states.attendance(a, AttendanceStatus.ATTENDED, "앱 체크인", "USER:" + userId);
        chatService.postSystem(a.getTableId(), "참가자 1명이 도착했어요. 🙌");
    }

    /**
     * 종료된 회차 정산: 미체크인 → 노쇼 후보(운영자는 후보만 확인), 참석자 → 피드백 요청.
     * 이후 24시간 지나도 피드백이 없으면 1회 리마인드 (다음 신청은 막지 않음).
     */
    @Transactional
    public int settleFinishedEvents() {
        LocalDateTime now = clock.now();
        int processed = 0;
        for (EventSlot slot : slotRepository.findByStatusIn(List.of(SlotStatus.MATCHED))) {
            if (now.isBefore(slot.endAt())) {
                continue;
            }
            for (DiningTable t : tableRepository.findBySlotIdAndStatus(slot.getId(), DiningTable.Status.CONFIRMED)) {
                for (Application a : applicationRepository.findByTableId(t.getId())) {
                    if (!a.isSeated()) {
                        continue;
                    }
                    if (a.getAttendanceStatus() == AttendanceStatus.SCHEDULED) {
                        states.attendance(a, AttendanceStatus.NO_SHOW, "종료 시각까지 체크인 없음 (노쇼 후보)", "SYSTEM");
                        exceptionCaseService.open(ExceptionType.NO_SHOW_REVIEW, "app:" + a.getId(),
                                slot.label() + " 테이블 " + t.getTableCode() + " 미체크인 - 노쇼 후보 확인",
                                ExceptionCaseService.Refs.application(a.getId(), a.getUserId()));
                    } else if (a.getAttendanceStatus() == AttendanceStatus.ATTENDED) {
                        notificationService.notify(a.getUserId(), NotificationType.FEEDBACK_REQUEST, Map.of(),
                                "app:" + a.getId(), a.getId(), t.getId());
                    }
                    processed++;
                }
                t.setStatus(DiningTable.Status.COMPLETED);
            }
            slot.setStatus(SlotStatus.COMPLETED);
        }
        // 피드백 미응답 1회 리마인드
        for (Application a : applicationRepository.findByMatchStatus(MatchStatus.CONFIRMED)) {
            if (a.getAttendanceStatus() != AttendanceStatus.ATTENDED || feedbackRepository.existsByApplicationId(a.getId())) {
                continue;
            }
            EventSlot slot = slotRepository.findById(a.getAssignedSlotId()).orElse(null);
            if (slot != null && now.isAfter(slot.endAt().plusHours(24))) {
                notificationService.notify(a.getUserId(), NotificationType.FEEDBACK_REMINDER, Map.of(),
                        "app:" + a.getId(), a.getId(), a.getTableId());
            }
        }
        return processed;
    }

    /** 운영자: 노쇼 후보 확정 또는 참석으로 정정 */
    @Transactional
    public void reviewNoShow(Long applicationId, boolean confirmNoShow, String note, String actor) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> ApiException.notFound("신청"));
        if (!confirmNoShow) {
            states.attendance(a, AttendanceStatus.ATTENDED, "운영자 정정: " + note, actor);
        }
        exceptionCaseService.autoResolve(ExceptionType.NO_SHOW_REVIEW, "app:" + applicationId,
                (confirmNoShow ? "노쇼 확정" : "참석으로 정정") + (note == null ? "" : " - " + note));
    }
}
