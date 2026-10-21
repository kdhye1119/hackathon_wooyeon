package com.wassup.dinner.application.domain;

/** 매칭 상태 match_status */
public enum MatchStatus {
    WAITING, MATCHING, REALLOCATING, CONFIRMED, ALTERNATIVE_OFFERED, TRANSFERRED, NO_MATCH, EXCEPTION;

    public boolean isActive() {
        return this == WAITING || this == MATCHING || this == REALLOCATING || this == CONFIRMED
                || this == ALTERNATIVE_OFFERED || this == EXCEPTION;
    }
}
