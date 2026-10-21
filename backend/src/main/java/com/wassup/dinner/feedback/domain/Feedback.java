package com.wassup.dinner.feedback.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 참가 후 개인별 만족도 피드백. 신청 1건당 1회. */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long applicationId;

    private Long userId;
    private Long tableId;

    /** 1~5 */
    private int tableRating;
    private int conversationRating;
    private int venueRating;
    /** 재참여 의향 1~5 */
    private int rejoinIntent;
    /** 추천 의향 0~10 (NPS) */
    private int recommendScore;

    @Column(length = 1000)
    private String comment;

    /** 참가자가 '후기로 공개'를 고르면 추천 의향·한마디가 이름 없이 홈 화면 후기에 보인다 */
    @Column(columnDefinition = "boolean default false", nullable = false)
    private boolean publicReview;

    /** 운영자가 공개 후기를 숨김 처리 */
    @Column(columnDefinition = "boolean default false", nullable = false)
    private boolean hidden;

    private LocalDateTime createdAt;

    public Feedback(Long applicationId, Long userId, Long tableId, LocalDateTime now) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.tableId = tableId;
        this.createdAt = now;
    }
}
