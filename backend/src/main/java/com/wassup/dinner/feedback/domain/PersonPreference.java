package com.wassup.dinner.feedback.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사람별 선호 (비공개). WANT_AGAIN 은 다음 매칭에서 재회 감점을 줄이고 학습 성향에 반영,
 * AVOID 는 이후 같은 테이블 배치를 금지한다(필수 조건).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"fromUserId", "toUserId", "tableId"}),
        indexes = {@Index(columnList = "fromUserId"), @Index(columnList = "toUserId")})
public class PersonPreference {

    public enum Type { WANT_AGAIN, AVOID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long fromUserId;
    private Long toUserId;
    private Long tableId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Type type;

    private LocalDateTime createdAt;

    public PersonPreference(Long fromUserId, Long toUserId, Long tableId, Type type, LocalDateTime now) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.tableId = tableId;
        this.type = type;
        this.createdAt = now;
    }
}
