package com.wassup.dinner.matching.engine;

import com.wassup.dinner.matching.engine.MatchResult.PlannedTable;
import com.wassup.dinner.matching.engine.MatchResult.Stats;
import com.wassup.dinner.matching.engine.MatchResult.Unplaced;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 테이블 자동 매칭 엔진 (DB 와 무관한 순수 로직).
 *
 * <pre>
 * 1. 최초 조합: 1지망 회차별로 나이순 정렬 → 8살 이내 후보 창(window)에서 점수가 가장 오르는 사람을 탐욕적으로 추가
 *    (7명 이상 후보군은 4~6인 테이블 여러 개로 자동 분리)
 * 2. 국소 탐색: 같은 회차 테이블끼리 참가자 교환·이동으로 총점 개선
 * 3. 반복 (최대 4라운드)
 *    a. 검증: 4~6명·필수 조건·최소 점수 미달 테이블 해제 → 재배치 대상
 *    b. 재배치: 7.4 우선순위(같은 날짜·지역 → 같은 날짜·다른 지역 → 다른 날짜·같은 지역 → 다른 날짜·다른 지역)로
 *       기존 미확정 테이블(6명 미만)에 합류
 *    c. 병합: 남은 미배치자끼리 같은 회차 후보로 새 테이블 구성 (미완성 테이블 병합)
 * 4. 최종 검증을 통과한 테이블만 반환 → 서비스가 자동 확정
 * </pre>
 * 필수 조건(날짜·지역, 4~6명, 나이 차이, 제외 관계)은 어느 단계에서도 완화하지 않는다.
 */
public class MatchingEngine {

    public static final String VERSION = "greedy-localsearch-v1";
    private static final int MAX_ROUNDS = 4;
    private static final double IMPROVE_EPS = 0.3;

    private final MatchContext ctx;
    private final TableScorer scorer;

    private Map<Long, SlotInfo> slots;
    private Map<Long, List<Group>> groups;
    private List<Candidate> leftovers;
    private Map<Long, Integer> realloc;
    private Map<Long, String> placement;
    private Map<Long, String> lastFailure;
    private List<String> log;
    private int initialTables, splits, merges, reallocated, dissolved;

    public MatchingEngine(MatchContext ctx) {
        this.ctx = ctx;
        this.scorer = new TableScorer(ctx);
    }

    public TableScorer scorer() {
        return scorer;
    }

    private static final class Group {
        final long slotId;
        final List<Candidate> members;
        final String formation;

        Group(long slotId, List<Candidate> members, String formation) {
            this.slotId = slotId;
            this.members = new ArrayList<>(members);
            this.formation = formation;
        }
    }

    public synchronized MatchResult run(List<Candidate> candidates, Map<Long, SlotInfo> slotInfos) {
        slots = slotInfos;
        groups = new LinkedHashMap<>();
        leftovers = new ArrayList<>();
        realloc = new HashMap<>();
        placement = new HashMap<>();
        lastFailure = new HashMap<>();
        log = new ArrayList<>();
        initialTables = splits = merges = reallocated = dissolved = 0;

        // ---- 1. 최초 조합 (1지망 회차 기준)
        Map<Long, List<Candidate>> byPrimary = candidates.stream()
                .filter(c -> !c.slotOptions().isEmpty())
                .collect(Collectors.groupingBy(c -> c.slotOptions().get(0), TreeMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<Candidate>> e : byPrimary.entrySet()) {
            long slotId = e.getKey();
            List<Candidate> pool = e.getValue();
            List<Candidate> left = new ArrayList<>();
            List<Group> formed = form(pool, slotId, "INITIAL", left);
            groups.computeIfAbsent(slotId, k -> new ArrayList<>()).addAll(formed);
            initialTables += formed.size();
            if (formed.size() >= 2) {
                splits++;
                log("[분리] %s 후보 %d명 → %d개 테이블", label(slotId), pool.size(), formed.size());
            } else {
                log("[최초 조합] %s 후보 %d명 → %d개 테이블", label(slotId), pool.size(), formed.size());
            }
            for (Group g : formed) {
                for (Candidate c : g.members) {
                    placement.put(c.applicationId(), "1지망 회차(" + label(slotId) + ") 최초 조합으로 배치");
                }
            }
            for (Candidate c : left) {
                lastFailure.put(c.applicationId(), pool.size() < ctx.minTableSize()
                        ? "1지망 회차 신청 인원 부족(" + pool.size() + "명)"
                        : "1지망 회차에서 나이/제외 조건을 만족하는 4명 이상 조합 없음");
            }
            leftovers.addAll(left);
            improve(slotId);
        }
        if (!leftovers.isEmpty()) {
            log("[재배치 대상] 최초 조합 실패 %d명", leftovers.size());
        }

        // ---- 2~3. 검증 → 재배치 → 병합 반복
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            boolean changed = validateAndDissolve();
            changed |= joinExisting();
            changed |= mergeLeftovers();
            groups.keySet().forEach(this::improve);
            if (!changed) {
                break;
            }
        }
        validateAndDissolve();

        // ---- 4. 결과
        List<PlannedTable> tables = new ArrayList<>();
        for (List<Group> list : groups.values()) {
            for (Group g : list) {
                tables.add(new PlannedTable(g.slotId, List.copyOf(g.members), scorer.score(g.members), g.formation));
            }
        }
        List<Unplaced> unplaced = leftovers.stream()
                .map(c -> new Unplaced(c, unplacedReason(c, candidates))).toList();
        unplaced.forEach(u -> log("[매칭 실패] 신청#%d: %s", u.candidate().applicationId(), u.reason()));

        Stats stats = new Stats(candidates.size(), initialTables, splits, merges, reallocated, dissolved,
                tables.size(), unplaced.size());
        log("[완료] 확정 %d개 테이블, 재배치 성공 %d명, 실패 %d명", tables.size(), reallocated, unplaced.size());
        return new MatchResult(tables, unplaced, Map.copyOf(realloc), Map.copyOf(placement), stats, List.copyOf(log));
    }

    // ------------------------------------------------------------------ 조합 생성

    /** 후보군을 나이순으로 훑으며 4~6인 테이블을 만든다. 테이블에 못 들어간 사람은 left 에 담는다. */
    private List<Group> form(List<Candidate> pool, long slotId, String formation, List<Candidate> left) {
        List<Candidate> rem = new ArrayList<>(pool);
        rem.sort(Comparator.comparingInt(Candidate::birthYear).thenComparingLong(Candidate::applicationId));
        List<Group> result = new ArrayList<>();
        while (!rem.isEmpty()) {
            Candidate seed = rem.remove(0);
            List<Candidate> window = rem.stream()
                    .filter(x -> x.birthYear() - seed.birthYear() <= ctx.maxAgeGap())
                    .filter(x -> !ctx.excluded(seed.userId(), x.userId()))
                    .toList();
            int m = window.size() + 1;
            if (m < ctx.minTableSize()) {
                left.add(seed);
                continue;
            }
            int t = (int) Math.ceil(m / (double) ctx.maxTableSize());
            int size = Math.max(ctx.minTableSize(), Math.min(ctx.maxTableSize(), (int) Math.ceil(m / (double) t)));

            List<Candidate> group = new ArrayList<>(List.of(seed));
            while (group.size() < size) {
                Candidate best = null;
                double bestScore = -1;
                for (Candidate x : window) {
                    if (group.contains(x) || !scorer.canAdd(group, x)) {
                        continue;
                    }
                    List<Candidate> trial = new ArrayList<>(group);
                    trial.add(x);
                    // 동점이면 나이가 가까운 사람 우선
                    double s = scorer.score(trial).total() - 0.01 * (x.birthYear() - seed.birthYear());
                    if (s > bestScore) {
                        bestScore = s;
                        best = x;
                    }
                }
                if (best == null) {
                    break;
                }
                group.add(best);
            }
            if (group.size() < ctx.minTableSize()) {
                left.add(seed);
                continue;
            }
            rem.removeAll(group);
            result.add(new Group(slotId, group, formation));
        }
        return result;
    }

    // ------------------------------------------------------------------ 검증·해제

    private boolean validateAndDissolve() {
        boolean changed = false;
        for (List<Group> list : groups.values()) {
            Iterator<Group> it = list.iterator();
            while (it.hasNext()) {
                Group g = it.next();
                String v = scorer.violation(g.members, g.slotId, true);
                double total = scorer.score(g.members).total();
                if (v == null && total >= ctx.minScore()) {
                    continue;
                }
                String why = v != null ? v : "조합 점수 " + total + "점 (최소 " + ctx.minScore() + "점)";
                log("[해제] %s 테이블(%d명): %s", label(g.slotId), g.members.size(), why);
                for (Candidate c : g.members) {
                    lastFailure.put(c.applicationId(), "배치된 테이블이 조건 미충족으로 해제됨 - " + why);
                    placement.remove(c.applicationId());
                }
                leftovers.addAll(g.members);
                it.remove();
                dissolved++;
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ 재배치 (기존 테이블 합류)

    private boolean joinExisting() {
        boolean changed = false;
        leftovers.sort(Comparator.comparingInt((Candidate c) -> c.slotOptions().size())
                .thenComparingInt(Candidate::birthYear));
        Iterator<Candidate> it = leftovers.iterator();
        while (it.hasNext()) {
            Candidate c = it.next();
            Group target = null;
            double bestScore = -1;
            // 7.4 우선순위 단계별로 탐색, 같은 단계 안에서는 점수가 가장 높은 테이블
            for (List<Long> tier : priorityTiers(c)) {
                for (long slotId : tier) {
                    for (Group g : groups.getOrDefault(slotId, List.of())) {
                        if (g.members.size() >= ctx.maxTableSize() || !scorer.canAdd(g.members, c)) {
                            continue;
                        }
                        List<Candidate> trial = new ArrayList<>(g.members);
                        trial.add(c);
                        double s = scorer.score(trial).total();
                        if (scorer.isValid(trial, slotId) && s >= ctx.minScore() && s > bestScore) {
                            bestScore = s;
                            target = g;
                        }
                    }
                }
                if (target != null) {
                    break;
                }
            }
            if (target != null) {
                target.members.add(c);
                it.remove();
                reallocated++;
                realloc.merge(c.applicationId(), 1, Integer::sum);
                String prev = lastFailure.getOrDefault(c.applicationId(), "최초 조합 실패");
                placement.put(c.applicationId(), "자동 재배치: " + label(target.slotId) + " 테이블에 합류 (이전 사유: " + prev + ")");
                log("[재배치] 신청#%d → %s 테이블 합류 (%d명)", c.applicationId(), label(target.slotId), target.members.size());
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ 병합 (미배치자끼리 새 테이블)

    private boolean mergeLeftovers() {
        boolean changed = false;
        if (leftovers.size() < ctx.minTableSize()) {
            return false;
        }
        Map<Long, Long> demand = leftovers.stream().flatMap(c -> c.slotOptions().stream())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        List<Long> order = demand.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        for (long slotId : order) {
            List<Candidate> pool = leftovers.stream().filter(c -> c.slotOptions().contains(slotId)).toList();
            if (pool.size() < ctx.minTableSize()) {
                continue;
            }
            List<Candidate> left = new ArrayList<>();
            List<Group> formed = form(pool, slotId, "MERGED", left);
            for (Group g : formed) {
                if (scorer.score(g.members).total() < ctx.minScore()) {
                    continue;
                }
                groups.computeIfAbsent(slotId, k -> new ArrayList<>()).add(g);
                leftovers.removeAll(g.members);
                merges++;
                changed = true;
                log("[병합] %s 미배치 %d명으로 새 테이블 구성", label(slotId), g.members.size());
                for (Candidate c : g.members) {
                    reallocated++;
                    realloc.merge(c.applicationId(), 1, Integer::sum);
                    placement.put(c.applicationId(), "자동 재배치: " + label(slotId) + " 미완성 테이블 병합으로 배치");
                }
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ 국소 탐색

    /** 같은 회차 테이블끼리 교환·이동으로 총점을 올린다. 필수 조건은 항상 유지. */
    private void improve(long slotId) {
        List<Group> list = groups.getOrDefault(slotId, List.of());
        if (list.size() < 2) {
            return;
        }
        for (int iter = 0; iter < 30; iter++) {
            boolean improved = false;
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    improved |= trySwap(list.get(i), list.get(j));
                    improved |= tryMove(list.get(i), list.get(j));
                    improved |= tryMove(list.get(j), list.get(i));
                }
            }
            if (!improved) {
                return;
            }
        }
    }

    private boolean trySwap(Group a, Group b) {
        double base = scorer.score(a.members).total() + scorer.score(b.members).total();
        double floor = Math.min(ctx.minScore(), Math.min(scorer.score(a.members).total(), scorer.score(b.members).total()));
        for (int x = 0; x < a.members.size(); x++) {
            for (int y = 0; y < b.members.size(); y++) {
                List<Candidate> na = new ArrayList<>(a.members);
                List<Candidate> nb = new ArrayList<>(b.members);
                Candidate ca = na.set(x, b.members.get(y));
                nb.set(y, ca);
                if (!scorer.isValid(na, a.slotId) || !scorer.isValid(nb, b.slotId)) {
                    continue;
                }
                double sa = scorer.score(na).total();
                double sb = scorer.score(nb).total();
                if (sa + sb > base + IMPROVE_EPS && Math.min(sa, sb) >= floor) {
                    a.members.clear();
                    a.members.addAll(na);
                    b.members.clear();
                    b.members.addAll(nb);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryMove(Group from, Group to) {
        if (from.members.size() <= ctx.minTableSize() || to.members.size() >= ctx.maxTableSize()) {
            return false;
        }
        double base = scorer.score(from.members).total() + scorer.score(to.members).total();
        double floor = Math.min(ctx.minScore(), Math.min(scorer.score(from.members).total(), scorer.score(to.members).total()));
        for (Candidate c : List.copyOf(from.members)) {
            List<Candidate> nf = new ArrayList<>(from.members);
            nf.remove(c);
            List<Candidate> nt = new ArrayList<>(to.members);
            nt.add(c);
            if (!scorer.isValid(nf, from.slotId) || !scorer.isValid(nt, to.slotId)) {
                continue;
            }
            double sf = scorer.score(nf).total();
            double st = scorer.score(nt).total();
            if (sf + st > base + IMPROVE_EPS && Math.min(sf, st) >= floor) {
                from.members.remove(c);
                to.members.add(c);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 보조

    /**
     * 자동 재배치 우선순위 (PRD 7.4). 1지망 회차를 기준으로
     * 같은 날짜·같은 지역 → 같은 날짜·다른 지역 → 다른 날짜·같은 지역 → 다른 날짜·다른 지역.
     * 각 단계 안에서는 사용자가 고른 순서를 유지한다.
     */
    List<List<Long>> priorityTiers(Candidate c) {
        SlotInfo primary = slots.get(c.slotOptions().get(0));
        List<List<Long>> tiers = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            tiers.add(new ArrayList<>());
        }
        for (long slotId : c.slotOptions()) {
            SlotInfo s = slots.get(slotId);
            if (s == null) {
                continue;
            }
            boolean sameDate = s.date().equals(primary.date());
            boolean sameRegion = s.region().equals(primary.region());
            int tier = sameDate ? (sameRegion ? 0 : 1) : (sameRegion ? 2 : 3);
            tiers.get(tier).add(slotId);
        }
        return tiers;
    }

    private String unplacedReason(Candidate c, List<Candidate> all) {
        boolean anyPool = false;
        boolean anyApplicants = false;
        for (long slotId : c.slotOptions()) {
            long applicants = all.stream().filter(x -> x != c && x.slotOptions().contains(slotId)).count();
            if (applicants >= ctx.minTableSize() - 1) {
                anyApplicants = true;
            }
            long compatible = all.stream()
                    .filter(x -> x != c && x.slotOptions().contains(slotId))
                    .filter(x -> Math.abs(x.birthYear() - c.birthYear()) <= ctx.maxAgeGap())
                    .filter(x -> !ctx.excluded(c.userId(), x.userId()))
                    .count();
            if (compatible >= ctx.minTableSize() - 1) {
                anyPool = true;
            }
        }
        if (!anyApplicants) {
            return "신청하신 날짜/지역의 신청 인원이 최소 " + ctx.minTableSize() + "명에 미치지 못했어요.";
        }
        if (!anyPool) {
            return "신청하신 날짜/지역에 나이 차이 " + ctx.maxAgeGap() + "살 이내로 함께할 수 있는 참가자가 부족했어요.";
        }
        return "조건에 맞는 테이블의 남은 자리가 없거나, 조합 기준 점수를 충족하는 테이블을 만들지 못했어요.";
    }

    private String label(long slotId) {
        SlotInfo s = slots.get(slotId);
        return s == null ? "회차#" + slotId : s.label();
    }

    private void log(String fmt, Object... args) {
        log.add(String.format(fmt, args));
    }
}
