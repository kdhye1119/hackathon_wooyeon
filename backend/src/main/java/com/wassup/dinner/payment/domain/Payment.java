package com.wassup.dinner.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 이용권 구매 결제 1건 (해커톤: 모의 PG). */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    public enum Status { PENDING, PAID, FAILED, PARTIALLY_REFUNDED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketProduct product;

    private int amount;
    private int refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Status status = Status.PENDING;

    private String method = "MOCK_CARD";
    private String pgTxId;
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    @Version
    private Long version;

    public Payment(Long userId, TicketProduct product, LocalDateTime now) {
        this.userId = userId;
        this.product = product;
        this.amount = product.getPrice();
        this.createdAt = now;
    }

    public void addRefund(int refund) {
        this.refundedAmount += refund;
        this.status = refundedAmount >= amount ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
    }
}
