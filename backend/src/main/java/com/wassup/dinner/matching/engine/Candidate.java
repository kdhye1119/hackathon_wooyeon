package com.wassup.dinner.matching.engine;

import java.util.List;
import java.util.Set;

/**
 * 매칭 후보 1명 (결제 완료된 신청 1건).
 *
 * @param slotOptions   이번 실행 대상 회차 중 사용자가 고른 회차 (사용자 선택 순서 = 우선순위)
 * @param learnedTraits 과거 '다시 만나고 싶은 사람'들이 가진 성향 (참여가 쌓일수록 늘어남)
 * @param learningLevel 반영 가능한 과거 피드백 수. 클수록 학습 성향 비중이 커진다.
 */
public record Candidate(
        long applicationId,
        long userId,
        int birthYear,
        String gender,
        String mbti,
        Set<String> interests,
        Set<String> traits,
        Set<String> desiredTypes,
        Set<String> learnedTraits,
        int learningLevel,
        List<Long> slotOptions) {
}
