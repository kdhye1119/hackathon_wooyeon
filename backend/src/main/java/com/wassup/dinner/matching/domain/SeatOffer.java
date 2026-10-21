package com.wassup.dinner.matching.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 취소석 제안 (STA-07): 확정 테이블에 자리가 나면 조건에 맞는 대기자 한 명에게 참가 의사를 묻는다.
 * 응답 기한 안에 수락하면 합류, 거절·만료되면 다음 대기자에게 넘어간다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {@Index(columnList = "tableId"), @Index(columnList = "applicationId")})
public class SeatOffer {

    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tableId;
    private Long slotId;
    private Long applicationId;
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.PENDING;

    private LocalDateTime offeredAt;
    private LocalDateTime expiresAt;
    private LocalDateTime respondedAt;

    public SeatOffer(Long tableId, Long slotId, Long applicationId, Long userId, LocalDateTime now, LocalDateTime expiresAt) {
        this.tableId = tableId;
        this.slotId = slotId;
        this.applicationId = applicationId;
        this.userId = userId;
        this.offeredAt = now;
        this.expiresAt = expiresAt;
    }
}
