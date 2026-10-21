package com.wassup.dinner.matching.application;

import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.domain.MatchingPolicy;
import com.wassup.dinner.matching.domain.MatchingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingPolicyService {

    private final MatchingPolicyRepository repository;
    private final AppClock clock;

    /** 테이블 인원·최대 나이 차이는 선택 항목 (보내지 않으면 기존 값 유지) */
    public record PolicyRequest(double ageWeight, double genderWeight, double mbtiWeight, double interestWeight,
                                double desireWeight, double newnessWeight, double minScore,
                                Integer maxAgeGap, Integer minTableSize, Integer maxTableSize) {
    }

    @Transactional
    public MatchingPolicy get() {
        return repository.findById(MatchingPolicy.SINGLETON_ID).orElseGet(() -> repository.save(new MatchingPolicy()));
    }

    /** 필수 조건(나이 차이 8살, 4~6명)은 PRD 고정값이라 수정 대상에서 제외한다. */
    @Transactional
    public MatchingPolicy update(PolicyRequest r, String actor) {
        if (r.minScore() < 0 || r.minScore() > 100) {
            throw new IllegalArgumentException("최소 점수는 0~100 사이여야 합니다.");
        }
        double sum = r.ageWeight() + r.genderWeight() + r.mbtiWeight() + r.interestWeight() + r.desireWeight() + r.newnessWeight();
        if (sum <= 0) {
            throw new IllegalArgumentException("가중치 합은 0보다 커야 합니다.");
        }
        MatchingPolicy p = get();
        int min = r.minTableSize() == null ? p.getMinTableSize() : r.minTableSize();
        int max = r.maxTableSize() == null ? p.getMaxTableSize() : r.maxTableSize();
        int gap = r.maxAgeGap() == null ? p.getMaxAgeGap() : r.maxAgeGap();
        if (min < 2 || max > 10 || min > max) {
            throw new IllegalArgumentException("테이블 인원은 2~10명, 최소 인원이 최대 인원보다 클 수 없습니다.");
        }
        if (gap < 1 || gap > 30) {
            throw new IllegalArgumentException("최대 나이 차이는 1~30살 사이여야 합니다.");
        }
        p.setMinTableSize(min);
        p.setMaxTableSize(max);
        p.setMaxAgeGap(gap);
        p.setAgeWeight(r.ageWeight());
        p.setGenderWeight(r.genderWeight());
        p.setMbtiWeight(r.mbtiWeight());
        p.setInterestWeight(r.interestWeight());
        p.setDesireWeight(r.desireWeight());
        p.setNewnessWeight(r.newnessWeight());
        p.setMinScore(r.minScore());
        p.setUpdatedAt(clock.now());
        p.setUpdatedBy(actor);
        return p;
    }
}
