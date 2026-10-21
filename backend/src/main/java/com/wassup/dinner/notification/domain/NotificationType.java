package com.wassup.dinner.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 알림 시나리오와 기본 템플릿. 운영자가 MessageTemplate 으로 문구를 수정할 수 있다. {변수} 치환. */
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    APPLICATION_SUBMITTED("신청이 접수되었어요",
            "{slots} 신청이 완료되었어요. 매칭 전까지는 앱에서 신청을 취소할 수 있어요."),
    PAYMENT_PENDING("결제를 완료해 주세요",
            "{product} {amount}원 결제를 {deadline}까지 완료해야 매칭 후보에 포함돼요."),
    PAYMENT_COMPLETED("결제가 완료되었어요",
            "{product} 결제가 완료되었어요. 자동 매칭은 {matchAt}에 진행되고 결과를 바로 알려드릴게요."),
    REALLOCATING("조건에 맞는 다른 테이블을 찾고 있어요",
            "처음 구성된 테이블이 조건을 충족하지 못해, 신청하신 날짜·지역 안에서 다른 테이블을 찾고 있어요."),
    MATCH_CONFIRMED("테이블이 확정되었어요",
            "{slot} · {venue} · 집합 {time}. 앱 단체방에서 테이블 큐레이션과 안내를 확인해 보세요."),
    MATCH_FAILED("이번에는 테이블이 만들어지지 않았어요",
            "{reason} 다른 일정으로 이동하거나, 이용권 보관·환불 중 하나를 선택해 주세요."),
    TRANSFERRED("일정이 변경되었어요",
            "{slot} 회차로 이동했어요. 기존 결제는 그대로 유지되며 자동 매칭은 {matchAt}에 진행돼요."),
    TICKET_KEPT("이용권을 보관했어요",
            "이용권 1회가 복구되었어요. 유효기간({expiresAt}) 안에 다음 신청에 사용할 수 있어요."),
    REFUND_REQUESTED("환불을 요청했어요",
            "{amount}원 환불을 처리하고 있어요."),
    REFUND_COMPLETED("환불이 완료되었어요",
            "{amount}원이 원결제수단으로 환불되었어요."),
    CANCELED("신청이 취소되었어요",
            "{reason}"),
    TABLE_CHANGED("테이블 구성이 변경되었어요",
            "{reason}"),
    EVENT_REMINDER("내일 우연한 식탁에서 만나요",
            "{slot} · {venue} ({address}) · 예약명 {reservationName}. 당일 취소 및 노쇼 시 이후 참여가 제한될 수 있어요."),
    FEEDBACK_REQUEST("오늘 식탁은 어떠셨나요?",
            "만족도와 다시 만나고 싶은 분을 남겨주시면 다음 매칭에 반영돼요. 응답은 상대에게 공개되지 않아요."),
    FEEDBACK_REMINDER("피드백을 아직 남기지 않으셨어요",
            "1분이면 충분해요. 남겨주신 의견은 다음 테이블 구성에 반영돼요."),
    NEXT_RECOMMENDATION("다음 식탁을 추천해요",
            "{slot} 모집이 시작됐어요. {reason}");

    private final String defaultTitle;
    private final String defaultBody;
}
