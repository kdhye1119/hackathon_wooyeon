package com.wassup.dinner.matching.application;

import com.wassup.dinner.application.application.ResolutionService;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.chat.application.TableBriefing;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchingPolicy;
import com.wassup.dinner.matching.engine.Candidate;
import com.wassup.dinner.matching.engine.MatchContext;
import com.wassup.dinner.matching.engine.ScoreBreakdown;
import com.wassup.dinner.matching.engine.TableScorer;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import com.wassup.dinner.venue.application.VenueService;
import com.wassup.dinner.venue.domain.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 확정 테이블의 저장·후속 자동화(식당·단체방·안내)와 확정 후 인원 변동 처리. */
@Service
@RequiredArgsConstructor
public class TableService {

    private final DiningTableRepository tableRepository;
    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final ApplicationStates states;
    private final VenueService venueService;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final ResolutionService resolutionService;
    private final RelationshipService relationshipService;
    private final MatchingPolicyService policyService;
    private final AppClock clock;

    /** 엔진 평가 결과를 테이블에 기록하고 큐레이션 문구를 만든다. */
    public void applyScore(DiningTable t, ScoreBreakdown s, List<Candidate> members) {
        t.setMemberCount(members.size());
        t.setTotalScore(s.total());
        t.setAgeScore(s.age());
        t.setGenderScore(s.gender());
        t.setMbtiScore(s.mbti());
        t.setInterestScore(s.interest());
        t.setDesireScore(s.desire());
        t.setNewnessScore(s.newness());
        t.setMinBirthYear(s.minBirthYear());
        t.setMaxBirthYear(s.maxBirthYear());
        t.setMaleCount(s.male());
        t.setFemaleCount(s.female());
        t.setMetBeforePairs(s.metBeforePairs());
        t.setMbtiSummary(String.join(", ", s.mbtis()));
        t.setCommonInterests(new LinkedHashSet<>(s.commonInterests()));
        List<String> traits = members.stream().flatMap(c -> c.traits().stream()).toList();
        t.setCuration(TableBriefing.curation(members.size(), s.minBirthYear(), s.maxBirthYear(), s.male(), s.female(),
                s.commonInterests(), s.mbtis(), mostCommon(traits), clock.today().getYear()));
    }

    /** 확정 직후 자동화: 식당 배정 → 단체방 생성·공지·리마인드 예약 → 확정 알림 */
    @Transactional
    public void afterConfirm(DiningTable table, EventSlot slot, List<Application> members, Map<Long, User> users) {
        Optional<Venue> venue = venueService.assign(table, slot,
                members.stream().map(a -> users.get(a.getUserId()).getProfile().getPriceRanges()).toList());
        chatService.createForTable(table, slot, venue.orElse(null));
        for (Application a : members) {
            notificationService.notify(a.getUserId(), NotificationType.MATCH_CONFIRMED,
                    NotificationService.vars("slot", slot.label(), "venue", venue.map(Venue::getName).orElse("식당 배정 중"),
                            "time", slot.getStartTime().toString()),
                    "app:" + a.getId(), a.getId(), table.getId());
        }
    }

    /** 다음 테이블 코드 (회차 내 A, B, C ...) */
    public String nextCode(Long slotId, int offset) {
        long n = tableRepository.countBySlotId(slotId) + offset;
        StringBuilder sb = new StringBuilder();
        do {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    // ------------------------------------------------------------------ 확정 후 취소 처리 (PRD 15)

    /**
     * 확정 테이블에서 한 명이 빠진 뒤 호출.
     * 1) 조건에 맞는 대기자(같은 회차를 희망했던 매칭 실패자)를 자동 충원
     * 2) 그래도 4명 미만이면 남은 참가자를 같은 회차의 다른 테이블로 자동 재배치, 안 되면 대체 일정 안내
     */
    @Transactional
    public void handleSeatReleased(Long tableId) {
        DiningTable table = tableRepository.findById(tableId).orElseThrow();
        if (table.getStatus() != DiningTable.Status.CONFIRMED) {
            return;
        }
        EventSlot slot = slotRepository.findById(table.getSlotId()).orElseThrow();
        MatchingPolicy policy = policyService.get();
        boolean eventStarted = !clock.now().isBefore(slot.startAt());

        List<Application> seated = seated(tableId);
        if (!eventStarted && seated.size() < policy.getMaxTableSize()) {
            refill(table, slot, seated, policy);
            seated = seated(tableId);
        }
        if (seated.size() >= policy.getMinTableSize()) {
            rescore(table, seated, policy);
            chatService.postSystem(tableId, "참가자 구성이 변경되어 현재 " + seated.size() + "명이 함께해요.");
            return;
        }
        if (eventStarted) {
            return; // 행사 시작 이후에는 그대로 진행
        }
        dissolve(table, slot, seated, policy);
    }

    private void refill(DiningTable table, EventSlot slot, List<Application> seated, MatchingPolicy policy) {
        List<Application> waitlist = applicationRepository.findByMatchStatus(MatchStatus.ALTERNATIVE_OFFERED).stream()
                .filter(a -> a.getAppStatus() == ApplicationStatus.SUBMITTED)
                .filter(a -> a.getSlotIds().contains(slot.getId()))
                .filter(a -> a.getPaymentStatus() == PaymentStatus.REFUND_AVAILABLE)
                .sorted(Comparator.comparing(Application::getFailedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (waitlist.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        seated.forEach(a -> userIds.add(a.getUserId()));
        waitlist.forEach(a -> userIds.add(a.getUserId()));
        Map<Long, User> users = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        MatchContext ctx = relationshipService.context(policy, userIds);
        TableScorer scorer = new TableScorer(ctx);
        List<Candidate> members = seated.stream()
                .map(a -> RelationshipService.toCandidate(a, users.get(a.getUserId()), List.of(slot.getId()), null))
                .collect(Collectors.toCollection(ArrayList::new));
        for (Application w : waitlist) {
            if (members.size() >= policy.getMaxTableSize()) {
                break;
            }
            Candidate c = RelationshipService.toCandidate(w, users.get(w.getUserId()), List.of(slot.getId()), null);
            List<Candidate> trial = new ArrayList<>(members);
            trial.add(c);
            if (!scorer.canAdd(members, c) || scorer.score(trial).total() < policy.getMinScore()) {
                continue;
            }
            members.add(c);
            w.setTableId(table.getId());
            w.setAssignedSlotId(slot.getId());
            w.setConfirmedAt(clock.now());
            w.setReallocationCount(w.getReallocationCount() + 1);
            states.payment(w, PaymentStatus.PAID, "취소석 자동 충원으로 결제 유지", "SYSTEM");
            states.match(w, MatchStatus.CONFIRMED, "취소석 자동 충원: " + slot.label() + " 테이블 " + table.getTableCode(), "SYSTEM");
            states.attendance(w, AttendanceStatus.SCHEDULED, null, "SYSTEM");
            resolutionService.closeAsRefilled(w.getId(), slot.getId());
            notificationService.notify(w.getUserId(), NotificationType.MATCH_CONFIRMED,
                    NotificationService.vars("slot", slot.label(), "venue", "단체방 확인", "time", slot.getStartTime().toString()),
                    "app:" + w.getId() + ":refill", w.getId(), table.getId());
            chatService.postSystem(table.getId(), "새로운 참가자 1명이 합류했어요. 반갑게 맞아주세요!");
        }
    }

    private void dissolve(DiningTable table, EventSlot slot, List<Application> remaining, MatchingPolicy policy) {
        table.setStatus(DiningTable.Status.DISSOLVED);
        table.setDissolvedAt(clock.now());
        venueService.release(table);
        chatService.close(table.getId(), "참가자 이탈로 테이블 인원이 " + policy.getMinTableSize()
                + "명 미만이 되어 다른 테이블로 자동 재배치합니다. 단체방은 종료돼요.");

        // 같은 회차의 다른 확정 테이블로 재배치
        List<DiningTable> others = tableRepository.findBySlotIdAndStatus(slot.getId(), DiningTable.Status.CONFIRMED);
        for (Application a : remaining) {
            states.match(a, MatchStatus.REALLOCATING, "테이블 인원 부족으로 자동 재배치 중", "SYSTEM");
            notificationService.notify(a.getUserId(), NotificationType.REALLOCATING, Map.of(),
                    "app:" + a.getId() + ":table:" + table.getId(), a.getId(), table.getId());
            DiningTable target = findSeat(a, slot, others, policy);
            if (target != null) {
                a.setTableId(target.getId());
                a.setReallocationCount(a.getReallocationCount() + 1);
                a.setConfirmedAt(clock.now());
                states.match(a, MatchStatus.CONFIRMED, "자동 재배치: 테이블 " + target.getTableCode() + " 합류", "SYSTEM");
                rescore(target, seated(target.getId()), policy);
                chatService.postSystem(target.getId(), "새로운 참가자 1명이 합류했어요. 반갑게 맞아주세요!");
                notificationService.notify(a.getUserId(), NotificationType.TABLE_CHANGED,
                        NotificationService.vars("reason", slot.label() + " 테이블 " + target.getTableCode() + "(으)로 자동 재배치되었어요. 단체방을 확인해 주세요."),
                        "app:" + a.getId() + ":moved:" + target.getId(), a.getId(), target.getId());
            } else {
                a.setTableId(null);
                a.setAttendanceStatus(null);
                resolutionService.offer(a, "함께하던 참가자의 취소로 테이블이 해제되었고, 같은 회차에 합류할 수 있는 테이블이 없었어요.");
            }
        }
    }

    private DiningTable findSeat(Application a, EventSlot slot, List<DiningTable> tables, MatchingPolicy policy) {
        DiningTable best = null;
        double bestScore = -1;
        for (DiningTable t : tables) {
            List<Application> seated = seated(t.getId());
            if (seated.size() >= policy.getMaxTableSize()) {
                continue;
            }
            Set<Long> ids = seated.stream().map(Application::getUserId).collect(Collectors.toSet());
            ids.add(a.getUserId());
            Map<Long, User> users = userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
            TableScorer scorer = new TableScorer(relationshipService.context(policy, ids));
            List<Candidate> members = seated.stream()
                    .map(x -> RelationshipService.toCandidate(x, users.get(x.getUserId()), List.of(slot.getId()), null)).toList();
            Candidate c = RelationshipService.toCandidate(a, users.get(a.getUserId()), List.of(slot.getId()), null);
            if (!scorer.canAdd(members, c)) {
                continue;
            }
            List<Candidate> trial = new ArrayList<>(members);
            trial.add(c);
            double s = scorer.score(trial).total();
            if (s >= policy.getMinScore() && s > bestScore) {
                bestScore = s;
                best = t;
            }
        }
        return best;
    }

    /** 구성원 변동 후 점수·큐레이션 재계산 */
    public void rescore(DiningTable table, List<Application> seated, MatchingPolicy policy) {
        Set<Long> ids = seated.stream().map(Application::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        TableScorer scorer = new TableScorer(relationshipService.context(policy, ids));
        List<Candidate> members = seated.stream()
                .map(a -> RelationshipService.toCandidate(a, users.get(a.getUserId()), List.of(table.getSlotId()), null)).toList();
        applyScore(table, scorer.score(members), members);
    }

    public List<Application> seated(Long tableId) {
        return applicationRepository.findByTableId(tableId).stream().filter(Application::isSeated).toList();
    }

    private static List<String> mostCommon(List<String> traits) {
        return traits.stream().collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
    }
}
