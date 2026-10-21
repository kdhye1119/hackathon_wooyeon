package com.wassup.dinner.slot.domain;

public enum SlotStatus {
    /** 모집 중 */
    RECRUITING,
    /** 자동 매칭 실행 중 */
    MATCHING,
    /** 매칭 완료 (테이블 확정) */
    MATCHED,
    /** 행사 종료 (참석·노쇼 정산 완료) */
    COMPLETED,
    /** 운영 취소 */
    CANCELED
}
