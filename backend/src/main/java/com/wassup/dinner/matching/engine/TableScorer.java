package com.wassup.dinner.matching.engine;

import java.util.*;
import java.util.stream.Collectors;

/** 테이블 조합의 필수 조건 검증과 선호 조건 점수 계산. */
public class TableScorer {

    private final MatchContext ctx;

    public TableScorer(MatchContext ctx) {
        this.ctx = ctx;
    }

    // ------------------------------------------------------------------ 필수 조건

    /** 필수 조건 위반 사유. 문제가 없으면 null. */
    public String violation(List<Candidate> members, long slotId, boolean checkSize) {
        if (checkSize && (members.size() < ctx.minTableSize() || members.size() > ctx.maxTableSize())) {
            return "인원 " + members.size() + "명 (" + ctx.minTableSize() + "~" + ctx.maxTableSize() + "명 필요)";
        }
        if (ageGap(members) > ctx.maxAgeGap()) {
            return "연장자-막내 나이 차이 " + ageGap(members) + "살 (최대 " + ctx.maxAgeGap() + "살)";
        }
        for (Candidate c : members) {
            if (!c.slotOptions().contains(slotId)) {
                return "희망 날짜/지역 불일치";
            }
        }
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (ctx.excluded(members.get(i).userId(), members.get(j).userId())) {
                    return "차단/신고/비선호 관계 포함";
                }
            }
        }
        return null;
    }

    public boolean isValid(List<Candidate> members, long slotId) {
        return violation(members, slotId, true) == null;
    }

    /** 부분 조합에 한 명을 더해도 필수 조건(나이·제외 관계)을 지키는지 */
    public boolean canAdd(List<Candidate> members, Candidate x) {
        int min = x.birthYear();
        int max = x.birthYear();
        for (Candidate m : members) {
            if (ctx.excluded(m.userId(), x.userId())) {
                return false;
            }
            min = Math.min(min, m.birthYear());
            max = Math.max(max, m.birthYear());
        }
        return max - min <= ctx.maxAgeGap();
    }

    public static int ageGap(List<Candidate> members) {
        IntSummaryStatistics s = members.stream().mapToInt(Candidate::birthYear).summaryStatistics();
        return members.isEmpty() ? 0 : s.getMax() - s.getMin();
    }

    // ------------------------------------------------------------------ 선호 조건 점수

    public ScoreBreakdown score(List<Candidate> m) {
        int n = m.size();
        IntSummaryStatistics years = m.stream().mapToInt(Candidate::birthYear).summaryStatistics();
        int gap = n == 0 ? 0 : years.getMax() - years.getMin();

        // 1. 연령대: 나이 차이가 작을수록 좋다 (필수 조건 8살 안에서)
        double age = 1.0 - 0.6 * Math.min(1.0, gap / (double) Math.max(1, ctx.maxAgeGap()));

        // 2. 성비: 남녀 동수에 가까울수록 좋다
        int male = (int) m.stream().filter(c -> "MALE".equals(c.gender())).count();
        int female = n - male;
        double gender = n == 0 ? 1.0 : 1.0 - Math.abs(male - female) / (double) n;

        // 3. MBTI 궁합: 테이블 안 모든 쌍의 궁합표 점수 평균
        double pairSum = 0;
        int pairs = 0;
        int metBeforePairs = 0;
        double newnessSum = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Candidate a = m.get(i);
                Candidate b = m.get(j);
                pairSum += mbtiPair(a.mbti(), b.mbti());
                PairKey key = PairKey.of(a.userId(), b.userId());
                if (ctx.metBefore().contains(key)) {
                    metBeforePairs++;
                    newnessSum += ctx.wantAgain().contains(key) ? 0.7 : 0.0;
                } else {
                    newnessSum += 1.0;
                }
                pairs++;
            }
        }
        double mbti = pairs == 0 ? 1.0 : pairSum / pairs;

        // 4. 관심사: 2명 이상이 공유하는 관심사, 공통 관심사를 가진 사람 비율
        Map<String, Long> interestCount = m.stream().flatMap(c -> c.interests().stream())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        List<String> common = interestCount.entrySet().stream().filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        long withShared = m.stream().filter(c -> c.interests().stream().anyMatch(common::contains)).count();
        double interest = n == 0 ? 1.0 : 0.6 * withShared / n + 0.4 * Math.min(1.0, common.size() / 3.0);

        // 5. 만나고 싶은 사람: 각자의 희망 유형이 테이블 안에서 충족되는 정도 (+ 과거 피드백에서 학습한 선호)
        Map<Long, Boolean> satisfied = new LinkedHashMap<>();
        double desireSum = 0;
        for (Candidate c : m) {
            List<Candidate> others = m.stream().filter(o -> o != c).toList();
            double explicit = desireExplicit(c, others);
            double learned = desireLearned(c, others);
            double alpha = learned < 0 ? 0 : Math.min(0.5, 0.1 * c.learningLevel());
            double s = learned < 0 ? explicit : (1 - alpha) * explicit + alpha * learned;
            desireSum += s;
            satisfied.put(c.applicationId(), explicit >= 0.5);
        }
        double desire = n == 0 ? 1.0 : desireSum / n;

        // 6. 새로운 사람: 이전에 만난 쌍이 적을수록 좋다
        double newness = pairs == 0 ? 1.0 : newnessSum / pairs;

        MatchContext.Weights w = ctx.weights();
        double total = 100.0 * (w.age() * age + w.gender() * gender + w.mbti() * mbti + w.interest() * interest
                + w.desire() * desire + w.newness() * newness) / w.sum();

        return new ScoreBreakdown(round(total), round(age), round(gender), round(mbti), round(interest),
                round(desire), round(newness), n == 0 ? 0 : years.getMin(), n == 0 ? 0 : years.getMax(),
                male, female, common, m.stream().map(Candidate::mbti).toList(), satisfied, metBeforePairs);
    }

    /** MBTI 쌍 궁합 (0~1): mbti.md 궁합표 점수 / 100 */
    static double mbtiPair(String a, String b) {
        return MbtiCompatibility.score(a, b) / 100.0;
    }

    private static double desireExplicit(Candidate c, List<Candidate> others) {
        if (c.desiredTypes().isEmpty() || others.isEmpty()) {
            return 1.0;
        }
        int ok = 0;
        for (String d : c.desiredTypes()) {
            if (desireMet(d, c, others)) {
                ok++;
            }
        }
        return ok / (double) c.desiredTypes().size();
    }

    /** 과거 '다시 만나고 싶은 사람' 성향이 테이블에 있는 비율. 학습 데이터가 없으면 -1 */
    private static double desireLearned(Candidate c, List<Candidate> others) {
        if (c.learnedTraits() == null || c.learnedTraits().isEmpty() || others.isEmpty()) {
            return -1;
        }
        Set<String> present = others.stream().flatMap(o -> o.traits().stream()).collect(Collectors.toSet());
        long hit = c.learnedTraits().stream().filter(present::contains).count();
        return hit / (double) c.learnedTraits().size();
    }

    static boolean desireMet(String desired, Candidate me, List<Candidate> others) {
        return switch (desired) {
            case "SIMILAR" -> others.stream().filter(o -> similar(me, o)).count() * 2 >= others.size();
            case "DIFFERENT" -> others.stream().anyMatch(o -> !similar(me, o));
            case "DEEP_TALK" -> others.stream().anyMatch(o -> o.traits().contains("DEEP_TALK"));
            case "HUMOR" -> others.stream().anyMatch(o -> o.traits().contains("HUMOR"));
            case "PASSIONATE" -> others.stream().anyMatch(o -> o.traits().contains("ENERGETIC") || o.traits().contains("LEADER"));
            case "CALM_STABLE" -> others.stream().anyMatch(o -> o.traits().contains("CALM") || o.traits().contains("LISTENER"));
            default -> true;
        };
    }

    /** 성향이 하나 이상 겹치거나 MBTI 기질(NF/NT/SJ/SP)이 같으면 '비슷한 사람' */
    static boolean similar(Candidate a, Candidate b) {
        boolean traitOverlap = a.traits().stream().anyMatch(b.traits()::contains);
        return traitOverlap || Objects.equals(temperament(a.mbti()), temperament(b.mbti()));
    }

    static String temperament(String mbti) {
        if (mbti == null || mbti.length() != 4) {
            return null;
        }
        char sn = mbti.charAt(1);
        return sn == 'N' ? "N" + mbti.charAt(2) : "S" + mbti.charAt(3);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
