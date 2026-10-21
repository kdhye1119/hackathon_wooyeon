package com.wassup.dinner.safety.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 차단. 양방향으로 같은 테이블 배치를 금지한다. 상대에게 공개되지 않는다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"blockerId", "blockedId"}))
public class UserBlock {

    public enum Source { MANUAL, REPORT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long blockerId;
    private Long blockedId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Source source;

    private LocalDateTime createdAt;

    public UserBlock(Long blockerId, Long blockedId, Source source, LocalDateTime now) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.source = source;
        this.createdAt = now;
    }
}
