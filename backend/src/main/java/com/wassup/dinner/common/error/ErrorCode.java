package com.wassup.dinner.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    PROFILE_INCOMPLETE(HttpStatus.BAD_REQUEST, "프로필 필수 항목을 먼저 입력해 주세요."),
    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "개인정보 수집·이용 및 운영 정책 동의가 필요합니다."),
    ACCOUNT_RESTRICTED(HttpStatus.FORBIDDEN, "참가가 제한된 계정입니다."),
    SLOT_CLOSED(HttpStatus.BAD_REQUEST, "모집이 마감된 회차입니다."),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT, "이미 신청한 회차가 포함되어 있습니다."),
    TICKET_UNAVAILABLE(HttpStatus.BAD_REQUEST, "사용할 수 있는 이용권이 없습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없습니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "행사 당일에는 취소할 수 없습니다."),
    CHECKIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "체크인 가능 시간이 아닙니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출했습니다.");

    private final HttpStatus status;
    private final String message;
}
