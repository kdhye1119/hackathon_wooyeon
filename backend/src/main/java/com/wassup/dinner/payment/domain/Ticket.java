package com.wassup.dinner.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보유 이용권. 횟수권은 신청 1건마다 1회 차감, 매칭 실패 후 '보관' 선택 시 1회 복구.
 * 멤버십은 기간 안에 횟수 제한 없이 사용하고, 매칭 실패 시 실패 횟수만큼 기간을 연장한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketProduct product;

    private int totalCount;
    private int remainingCount;
    private LocalDateTime expiresAt;

    @Column(unique = true)
    private Long paymentId;

    private LocalDateTime createdAt;

    @Version
    private Long version;

    public Ticket(Long userId, TicketProduct product, Long paymentId, LocalDateTime now) {
        this.userId = userId;
        this.product = product;
        this.paymentId = paymentId;
        this.totalCount = product.getCount();
        this.remainingCount = product.getCount();
        this.expiresAt = now.plusDays(product.getValidDays());
        this.createdAt = now;
    }

    public boolean isMembership() {
        return product.isMembership();
    }

    public boolean isUsable(LocalDateTime now) {
        return (isMembership() || remainingCount > 0) && now.isBefore(expiresAt);
    }

    public void use(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("사용할 수 없는 이용권입니다.");
        }
        if (!isMembership()) {
            remainingCount--;
        }
    }

    public void restore() {
        if (!isMembership() && remainingCount < totalCount) {
            remainingCount++;
        }
    }

    /** 멤버십 매칭 실패 1회 → 이용 기한 연장 */
    public void extend(int days) {
        expiresAt = expiresAt.plusDays(days);
    }
}
