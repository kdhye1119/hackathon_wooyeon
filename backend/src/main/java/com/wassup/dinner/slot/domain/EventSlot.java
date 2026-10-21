package com.wassup.dinner.slot.domain;

import com.wassup.dinner.user.domain.Options.Region;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

/** 회차: 날짜 + 시간 + 지역 단위의 모집 단위. 하나의 회차 안에서 여러 테이블이 만들어진다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"event_date", "start_time", "region"}))
public class EventSlot {

    public static final int DURATION_HOURS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_date")
    private LocalDate date;
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Region region;

    /** 모집 정원 (참가자 수) */
    private int capacity;

    private LocalDateTime applyDeadline;
    /** 자동 매칭 실행 시각 (= 매칭 발표 예정 시각) */
    private LocalDateTime matchAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlotStatus status = SlotStatus.RECRUITING;

    private LocalDateTime createdAt;

    @Version
    private Long version;

    public EventSlot(LocalDate date, LocalTime startTime, Region region, int capacity,
                     LocalDateTime applyDeadline, LocalDateTime matchAt, LocalDateTime now) {
        this.date = date;
        this.startTime = startTime;
        this.region = region;
        this.capacity = capacity;
        this.applyDeadline = applyDeadline;
        this.matchAt = matchAt;
        this.createdAt = now;
    }

    public LocalDateTime startAt() {
        return date.atTime(startTime);
    }

    public LocalDateTime endAt() {
        return startAt().plusHours(DURATION_HOURS);
    }

    /** 이 시각 이후로는 환불 불가: 진행 2일 전 24:00 (목요일 행사 → 화요일 자정, FAQ Q1) */
    public LocalDateTime refundDeadline() {
        return date.minusDays(1).atStartOfDay();
    }

    public boolean isOpen(LocalDateTime now) {
        return status == SlotStatus.RECRUITING && now.isBefore(applyDeadline);
    }

    public String label() {
        return "%d/%d (%s) %s %s".formatted(date.getMonthValue(), date.getDayOfMonth(),
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                startTime, region.getLabel());
    }
}
