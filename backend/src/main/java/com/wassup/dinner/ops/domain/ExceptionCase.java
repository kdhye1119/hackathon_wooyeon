package com.wassup.dinner.ops.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 예외 처리함 항목. dedupKey 유니크로 같은 원인의 예외가 중복 등록되지 않는다. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "status"))
public class ExceptionCase {

    public enum Status { OPEN, RESOLVED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExceptionType type;

    private Long applicationId;
    private Long tableId;
    private Long slotId;
    private Long userId;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.OPEN;

    @Column(length = 1000)
    private String adminNote;
    private String resolvedBy;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @Column(unique = true, length = 150)
    private String dedupKey;

    public ExceptionCase(ExceptionType type, String dedupKey, String reason, LocalDateTime now) {
        this.type = type;
        this.dedupKey = dedupKey;
        this.reason = reason;
        this.createdAt = now;
    }
}
