package com.wassup.dinner.venue.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 식당 예약 (테이블 1개 = 예약 1건). tableId 유니크로 중복 배정을 막는다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "venueId,slotId"))
public class VenueBooking {

    public enum Status { BOOKED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long venueId;
    private Long slotId;

    @Column(unique = true, nullable = false)
    private Long tableId;

    private String reservationName;
    private int headCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.BOOKED;

    private LocalDateTime createdAt;

    public VenueBooking(Long venueId, Long slotId, Long tableId, String reservationName, int headCount, LocalDateTime now) {
        this.venueId = venueId;
        this.slotId = slotId;
        this.tableId = tableId;
        this.reservationName = reservationName;
        this.headCount = headCount;
        this.createdAt = now;
    }
}
