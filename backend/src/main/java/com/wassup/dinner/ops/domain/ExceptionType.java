package com.wassup.dinner.ops.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 자동 처리가 불가능해 운영자에게만 올라가는 예외 유형. */
@Getter
@RequiredArgsConstructor
public enum ExceptionType {
    PAYMENT_ERROR("결제 오류"),
    DATA_MISSING("데이터 누락"),
    VENUE_SHORTAGE("식당 부족"),
    UNRESOLVED_CONFLICT("해결되지 않은 조건 충돌"),
    NOTIFICATION_FAILED("안내/리마인드 발송 실패"),
    REFUND_FAILED("자동 환불 실패"),
    SAFETY_REPORT("안전 신고"),
    NO_SHOW_REVIEW("노쇼 후보 확인"),
    LATE_CANCEL("제한 시점 이후 취소");

    private final String label;
}
