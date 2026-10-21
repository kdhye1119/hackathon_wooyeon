package com.wassup.dinner.matching.engine;

import java.util.List;
import java.util.Map;

/**
 * 테이블 조합 평가 결과. 각 항목은 0~1, total 은 가중 합산 0~100.
 *
 * @param desireSatisfied 신청 ID → '만나고 싶은 사람' 조건 충족 여부
 */
public record ScoreBreakdown(
        double total,
        double age,
        double gender,
        double mbti,
        double interest,
        double desire,
        double newness,
        int minBirthYear,
        int maxBirthYear,
        int male,
        int female,
        List<String> commonInterests,
        List<String> mbtis,
        Map<Long, Boolean> desireSatisfied,
        int metBeforePairs) {

    public int ageGap() {
        return maxBirthYear - minBirthYear;
    }
}
