package com.wassup.dinner.common.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 주요 상태 변경 이력: 언제, 누가(SYSTEM/USER/ADMIN), 무엇을, 왜 바꿨는지. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(columnList = "entityType,entityId"))
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityType;
    private Long entityId;
    private String field;
    private String fromValue;
    private String toValue;
    @Column(length = 500)
    private String reason;
    private String actor;
    private LocalDateTime changedAt;

    public StatusHistory(String entityType, Long entityId, String field, String fromValue, String toValue,
                         String reason, String actor, LocalDateTime changedAt) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.field = field;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.reason = reason;
        this.actor = actor;
        this.changedAt = changedAt;
    }
}
