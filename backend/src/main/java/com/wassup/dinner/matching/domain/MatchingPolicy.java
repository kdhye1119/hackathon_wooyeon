package com.wassup.dinner.matching.domain;

import com.wassup.dinner.matching.engine.MatchContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 서비스 전체 매칭 정책 (단일 행). 운영자가 가중치와 최소 점수를 조정한다. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MatchingPolicy {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private double ageWeight = 25;
    private double genderWeight = 20;
    private double mbtiWeight = 15;
    private double interestWeight = 15;
    private double desireWeight = 10;
    private double newnessWeight = 15;

    /** 자동 확정 최소 점수 (0~100) */
    private double minScore = 55;
    private int maxAgeGap = 8;
    private int minTableSize = 4;
    private int maxTableSize = 6;

    private LocalDateTime updatedAt;
    private String updatedBy;

    public MatchContext.Weights weights() {
        return new MatchContext.Weights(ageWeight, genderWeight, mbtiWeight, interestWeight, desireWeight, newnessWeight);
    }
}
