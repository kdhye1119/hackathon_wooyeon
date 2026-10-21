package com.wassup.dinner.user.domain;

public enum AccountStatus {
    /** 정상 */
    ACTIVE,
    /** 경고 (참가 가능) */
    WARNED,
    /** 참가 제한 (기간) */
    RESTRICTED,
    /** 영구 제한 */
    BANNED,
    /** 탈퇴 (식별 정보 삭제됨) */
    WITHDRAWN;

    public boolean canApply() {
        return this == ACTIVE || this == WARNED;
    }
}
