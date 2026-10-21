package com.wassup.dinner.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 신청 1건에 대한 자동 환불. applicationId 유니크 제약으로 같은 신청이 두 번 환불되지 않는다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    public enum Status { REQUESTED, PROCESSING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long applicationId;

    private Long paymentId;
    private Long userId;
    private int amount;
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.REQUESTED;

    private int attempts;
    private String pgRefundId;
    private String failureReason;

    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    public Refund(Long applicationId, Long paymentId, Long userId, int amount, String reason, LocalDateTime now) {
        this.applicationId = applicationId;
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.reason = reason;
        this.requestedAt = now;
    }
}
