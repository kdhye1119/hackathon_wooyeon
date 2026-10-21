package com.wassup.dinner.chat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 단체방 예약 게시 (리마인드·대화 콘텐츠). 내용은 발송 시점의 최신 정보로 만든다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "status,scheduledAt"))
public class ScheduledRoomPost {

    public enum Kind { REMINDER, CONTENT }

    public enum Status { PENDING, SENT, FAILED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roomId;
    private Long tableId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Kind kind;

    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.PENDING;

    private int attempts;
    private String lastError;
    private LocalDateTime sentAt;

    @Column(unique = true, length = 100)
    private String dedupKey;

    public ScheduledRoomPost(Long roomId, Long tableId, Kind kind, LocalDateTime scheduledAt) {
        this.roomId = roomId;
        this.tableId = tableId;
        this.kind = kind;
        this.scheduledAt = scheduledAt;
        this.dedupKey = kind.name() + ":table:" + tableId;
    }
}
