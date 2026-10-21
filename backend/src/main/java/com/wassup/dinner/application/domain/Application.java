package com.wassup.dinner.application.domain;

import com.wassup.dinner.common.jpa.LongListConverter;
import com.wassup.dinner.payment.domain.TicketProduct;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 참가 신청 1건. 신청·결제·매칭·참석 상태를 각각 분리해 저장한다.
 * 상태 변경은 ApplicationStates 를 통해서만 하여 변경 이력을 남긴다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(columnList = "userId"),
        @Index(columnList = "matchStatus"),
        @Index(columnList = "tableId")
})
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** 희망 회차 ID 목록 (사용자가 고른 날짜·지역 순서 = 우선순위) */
    @Convert(converter = LongListConverter.class)
    @Column(length = 500)
    private List<Long> slotIds = new ArrayList<>();

    /** 서울권 외 희망 지역 (기타 입력, 수요 파악용) */
    private String otherRegion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketProduct product;
    private Long ticketId;
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApplicationStatus appStatus = ApplicationStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MatchStatus matchStatus = MatchStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AttendanceStatus attendanceStatus;

    /** 확정된 테이블 / 회차 */
    private Long tableId;
    private Long assignedSlotId;

    /** 자동 재배치 횟수와 최종 배치·실패 사유 */
    private int reallocationCount;
    @Column(length = 500)
    private String statusReason;

    private Long transferredFromId;
    private Long transferredToId;

    private LocalDateTime submittedAt;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime failedAt;
    private LocalDateTime checkedInAt;
    private LocalDateTime canceledAt;

    @Version
    private Long version;

    public Application(Long userId, List<Long> slotIds, String otherRegion, LocalDateTime now) {
        this.userId = userId;
        this.slotIds = new ArrayList<>(slotIds);
        this.otherRegion = otherRegion;
        this.submittedAt = now;
    }

    public boolean isActive() {
        return appStatus == ApplicationStatus.SUBMITTED && matchStatus.isActive();
    }

    /** 확정 테이블에 앉아 있는 상태 (취소하지 않음) */
    public boolean isSeated() {
        return appStatus == ApplicationStatus.SUBMITTED && matchStatus == MatchStatus.CONFIRMED && tableId != null;
    }

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }
}
