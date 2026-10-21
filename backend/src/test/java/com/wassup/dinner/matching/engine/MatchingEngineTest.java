package com.wassup.dinner.matching.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private static final long HONGDAE = 1L;
    private static final long JAMSIL = 2L;
    private static final long HONGDAE_NEXT_WEEK = 3L;
    private static final LocalDate D = LocalDate.of(2026, 10, 22);

    private final Map<Long, SlotInfo> slots = Map.of(
            HONGDAE, new SlotInfo(HONGDAE, D, "HONGDAE_HAPJEONG", "10/22 홍대"),
            JAMSIL, new SlotInfo(JAMSIL, D, "JAMSIL", "10/22 잠실"),
            HONGDAE_NEXT_WEEK, new SlotInfo(HONGDAE_NEXT_WEEK, D.plusDays(7), "HONGDAE_HAPJEONG", "10/29 홍대"));

    private long seq = 1;

    private Candidate c(int birthYear, String gender, String mbti, List<Long> slots, String... interests) {
        long id = seq++;
        return new Candidate(id, id, birthYear, gender, mbti, Set.of(interests.length == 0 ? new String[]{"TRAVEL"} : interests),
                Set.of("DEEP_TALK"), Set.of("DEEP_TALK"), Set.of(), 0, slots);
    }

    private MatchContext ctx(Set<PairKey> exclusions, Set<PairKey> metBefore) {
        return new MatchContext(exclusions, metBefore, Set.of(),
                new MatchContext.Weights(25, 20, 15, 15, 10, 15), 40, 8, 4, 6);
    }

    @Test
    void 후보군이_7명_이상이면_4에서_6인_테이블로_분리하고_필수조건을_지킨다() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            pool.add(c(1993 + (i % 6), i % 2 == 0 ? "MALE" : "FEMALE", i % 3 == 0 ? "ENFP" : "ISTJ", List.of(HONGDAE)));
        }
        MatchResult result = new MatchingEngine(ctx(Set.of(), Set.of())).run(pool, slots);

        assertThat(result.unplaced()).isEmpty();
        assertThat(result.tables()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.stats().splits()).isEqualTo(1);
        for (MatchResult.PlannedTable t : result.tables()) {
            assertThat(t.members().size()).isBetween(4, 6);
            assertThat(TableScorer.ageGap(t.members())).isLessThanOrEqualTo(8);
        }
        // 한 사람이 두 테이블에 배치되지 않는다
        List<Long> placed = result.tables().stream().flatMap(t -> t.members().stream()).map(Candidate::applicationId).toList();
        assertThat(placed).doesNotHaveDuplicates().hasSize(14);
    }

    @Test
    void 네명_미만_회차의_참가자는_사용자가_고른_다른_지역_테이블로_재배치된다() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pool.add(c(1995 + i, i % 2 == 0 ? "MALE" : "FEMALE", "ENFJ", List.of(HONGDAE)));
        }
        // 잠실 1지망 2명 (홍대 2지망) → 잠실 4명 미만 → 홍대로 재배치
        Candidate j1 = c(1996, "FEMALE", "INFP", List.of(JAMSIL, HONGDAE));
        Candidate j2 = c(1997, "MALE", "ESTP", List.of(JAMSIL, HONGDAE));
        pool.add(j1);
        pool.add(j2);

        MatchResult result = new MatchingEngine(ctx(Set.of(), Set.of())).run(pool, slots);

        assertThat(result.unplaced()).isEmpty();
        assertThat(result.reallocationCount()).containsKeys(j1.applicationId(), j2.applicationId());
        assertThat(result.tables()).allSatisfy(t -> assertThat(t.slotId()).isEqualTo(HONGDAE));
        assertThat(result.placementReason().get(j1.applicationId())).contains("자동 재배치");
    }

    @Test
    void 나이차이_8살_초과는_완화하지_않고_매칭_실패로_남긴다() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pool.add(c(1996, i % 2 == 0 ? "MALE" : "FEMALE", "ENFP", List.of(HONGDAE)));
        }
        Candidate old = c(1982, "MALE", "ISTJ", List.of(HONGDAE));
        pool.add(old);

        MatchResult result = new MatchingEngine(ctx(Set.of(), Set.of())).run(pool, slots);

        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables().get(0).members()).doesNotContain(old);
        assertThat(result.unplaced()).extracting(u -> u.candidate().applicationId()).containsExactly(old.applicationId());
        assertThat(result.unplaced().get(0).reason()).contains("8살");
    }

    @Test
    void 차단_신고_비선호_관계는_같은_테이블에_배치하지_않는다() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pool.add(c(1995 + (i % 4), i % 2 == 0 ? "MALE" : "FEMALE", "ENFP", List.of(HONGDAE)));
        }
        PairKey blocked = PairKey.of(pool.get(0).userId(), pool.get(1).userId());
        MatchResult result = new MatchingEngine(ctx(Set.of(blocked), Set.of())).run(pool, slots);

        for (MatchResult.PlannedTable t : result.tables()) {
            Set<Long> users = t.members().stream().map(Candidate::userId).collect(Collectors.toSet());
            assertThat(users.contains(blocked.a()) && users.contains(blocked.b())).isFalse();
        }
    }

    @Test
    void 이전에_만난_사람보다_새로운_사람과의_조합을_우선한다() {
        // 8명 → 4+4. 앞의 4명은 이미 한 테이블에서 만났던 사이
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pool.add(c(1996, i % 2 == 0 ? "MALE" : "FEMALE", "ENFP", List.of(HONGDAE)));
        }
        Set<PairKey> met = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                met.add(PairKey.of(pool.get(i).userId(), pool.get(j).userId()));
            }
        }
        MatchResult result = new MatchingEngine(ctx(Set.of(), met)).run(pool, slots);

        int metPairsInResult = result.tables().stream().mapToInt(t -> t.score().metBeforePairs()).sum();
        assertThat(metPairsInResult).isLessThan(6); // 그대로 다시 묶었다면 6쌍
    }

    @Test
    void 재배치_우선순위는_같은날짜_다른지역이_다른날짜_같은지역보다_먼저다() {
        Candidate x = c(1996, "MALE", "ENFP", List.of(HONGDAE_NEXT_WEEK, JAMSIL, HONGDAE));
        MatchingEngine engine = new MatchingEngine(ctx(Set.of(), Set.of()));
        engine.run(List.of(), slots);
        List<List<Long>> tiers = engine.priorityTiers(x);
        assertThat(tiers.get(0)).containsExactly(HONGDAE_NEXT_WEEK);
        // 1지망(10/29 홍대) 기준: 다른 날짜·같은 지역(10/22 홍대)이 3단계, 다른 날짜·다른 지역(10/22 잠실)이 4단계
        assertThat(tiers.get(2)).containsExactly(HONGDAE);
        assertThat(tiers.get(3)).containsExactly(JAMSIL);
    }
}
