package com.wassup.dinner.application.domain;

/** 신청 단위 결제 상태 payment_status */
public enum PaymentStatus {
    PENDING, PAID, FAILED, REFUND_AVAILABLE, REFUND_REQUESTED, REFUND_PROCESSING, REFUNDED, REFUND_FAILED
}
