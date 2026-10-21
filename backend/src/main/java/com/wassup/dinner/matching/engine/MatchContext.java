package com.wassup.dinner.matching.engine;

import java.util.Set;

/**
 * 매칭 실행에 필요한 관계·정책 정보.
 *
 * @param exclusions 같은 테이블 금지 쌍 (차단·신고·다시 만나고 싶지 않음) — 필수 조건
 * @param metBefore  이전에 같은 테이블에서 만난 쌍
 * @param wantAgain  한쪽이라도 '다시 만나고 싶다'고 선택한 쌍
 */
public record MatchContext(
        Set<PairKey> exclusions,
        Set<PairKey> metBefore,
        Set<PairKey> wantAgain,
        Weights weights,
        double minScore,
        int maxAgeGap,
        int minTableSize,
        int maxTableSize) {

    /** 선호 조건 가중치. 우선순위: 연령대 > 성비 > MBTI > 관심사 > 만나고 싶은 사람, + 새로운 사람 */
    public record Weights(double age, double gender, double mbti, double interest, double desire, double newness) {
        public double sum() {
            return age + gender + mbti + interest + desire + newness;
        }
    }

    public boolean excluded(long userA, long userB) {
        return exclusions.contains(PairKey.of(userA, userB));
    }
}
