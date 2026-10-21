package com.wassup.dinner.feedback.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 공개 후기 좋아요 (사람당 후기 1개에 1번) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"feedbackId", "userId"}))
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long feedbackId;
    private Long userId;
    private LocalDateTime createdAt;

    public ReviewLike(Long feedbackId, Long userId, LocalDateTime now) {
        this.feedbackId = feedbackId;
        this.userId = userId;
        this.createdAt = now;
    }
}
