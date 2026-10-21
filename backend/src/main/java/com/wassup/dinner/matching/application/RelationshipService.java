package com.wassup.dinner.matching.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.feedback.domain.PersonPreference;
import com.wassup.dinner.feedback.domain.PersonPreferenceRepository;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchingPolicy;
import com.wassup.dinner.matching.engine.Candidate;
import com.wassup.dinner.matching.engine.MatchContext;
import com.wassup.dinner.matching.engine.PairKey;
import com.wassup.dinner.safety.domain.SafetyReportRepository;
import com.wassup.dinner.safety.domain.UserBlockRepository;
import com.wassup.dinner.user.domain.Profile;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 매칭에 쓰는 사람 사이 관계 데이터.
 * - 제외(필수): 차단(양방향), 안전 신고(즉시 분리), '다시 만나고 싶지 않음'
 * - 이전 만남: 같은 테이블에 확정됐던 쌍 → 새로운 사람 우선
 * - 학습: '다시 만나고 싶은 사람'들의 성향 → 참여가 쌓일수록 개인 선호가 정교해짐
 */
@Service
@RequiredArgsConstructor
public class RelationshipService {

    private final UserBlockRepository blockRepository;
    private final SafetyReportRepository reportRepository;
    private final PersonPreferenceRepository preferenceRepository;
    private final ApplicationRepository applicationRepository;
    private final DiningTableRepository tableRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public record Learned(Set<String> traits, int level) {
        static final Learned NONE = new Learned(Set.of(), 0);
    }

    public MatchContext context(MatchingPolicy policy, Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return new MatchContext(Set.of(), Set.of(), Set.of(), policy.weights(), policy.getMinScore(),
                    policy.getMaxAgeGap(), policy.getMinTableSize(), policy.getMaxTableSize());
        }
        Set<PairKey> exclusions = new HashSet<>();
        blockRepository.findByBlockerIdInOrBlockedIdIn(userIds, userIds)
                .forEach(b -> exclusions.add(PairKey.of(b.getBlockerId(), b.getBlockedId())));
        reportRepository.findByReporterIdInOrReportedIdIn(userIds, userIds)
                .forEach(r -> exclusions.add(PairKey.of(r.getReporterId(), r.getReportedId())));
        Set<PairKey> wantAgain = new HashSet<>();
        for (PersonPreference p : preferenceRepository.findByFromUserIdInOrToUserIdIn(userIds, userIds)) {
            PairKey key = PairKey.of(p.getFromUserId(), p.getToUserId());
            if (p.getType() == PersonPreference.Type.AVOID) {
                exclusions.add(key);
            } else {
                wantAgain.add(key);
            }
        }
        wantAgain.removeAll(exclusions);
        return new MatchContext(exclusions, metBefore(userIds), wantAgain, policy.weights(), policy.getMinScore(),
                policy.getMaxAgeGap(), policy.getMinTableSize(), policy.getMaxTableSize());
    }

    /** 같은 테이블에 확정됐던 사용자 쌍 (해제된 테이블 제외) */
    public Set<PairKey> metBefore(Collection<Long> userIds) {
        List<Application> past = applicationRepository.findByUserIdInAndTableIdIsNotNull(userIds);
        Set<Long> validTables = tableRepository.findAllById(past.stream().map(Application::getTableId).collect(Collectors.toSet()))
                .stream().filter(t -> t.getStatus() != DiningTable.Status.DISSOLVED).map(DiningTable::getId)
                .collect(Collectors.toSet());
        Map<Long, List<Long>> byTable = past.stream()
                .filter(a -> validTables.contains(a.getTableId()))
                .collect(Collectors.groupingBy(Application::getTableId,
                        Collectors.mapping(Application::getUserId, Collectors.toList())));
        Set<PairKey> pairs = new HashSet<>();
        for (List<Long> members : byTable.values()) {
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    if (!members.get(i).equals(members.get(j))) {
                        pairs.add(PairKey.of(members.get(i), members.get(j)));
                    }
                }
            }
        }
        return pairs;
    }

    /** 사용자가 '다시 만나고 싶다'고 고른 사람들에게 많이 나타난 성향 상위 3개 */
    public Map<Long, Learned> learned(Collection<Long> userIds) {
        Map<Long, Learned> result = new HashMap<>();
        for (Long userId : userIds) {
            List<PersonPreference> likes = preferenceRepository.findByFromUserIdAndType(userId, PersonPreference.Type.WANT_AGAIN);
            int level = (int) feedbackRepository.countByUserId(userId);
            if (likes.isEmpty()) {
                result.put(userId, new Learned(Set.of(), level));
                continue;
            }
            Map<String, Long> freq = userRepository.findAllById(likes.stream().map(PersonPreference::getToUserId).toList())
                    .stream().flatMap(u -> u.getProfile().getTraits().stream())
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
            Set<String> top = freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(3).map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
            result.put(userId, new Learned(top, level));
        }
        return result;
    }

    public static Candidate toCandidate(Application a, User u, List<Long> options, Learned learned) {
        Profile p = u.getProfile();
        Learned l = learned == null ? Learned.NONE : learned;
        return new Candidate(a.getId(), u.getId(), p.getBirthYear(), p.getGender().name(), p.getMbti(),
                Set.copyOf(p.getInterests()), Set.copyOf(p.getTraits()), Set.copyOf(p.getDesiredTypes()),
                l.traits(), l.level(), List.copyOf(options));
    }
}
