package com.wassup.dinner.matching.application;

import com.wassup.dinner.application.application.ResolutionService;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.chat.application.TableBriefing;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchingPolicy;
import com.wassup.dinner.matching.domain.SeatOffer;
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

import java.time.LocalDateTime;
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
    private final com.wassup.dinner.matching.domain.SeatOfferRepository seatOfferRepository;

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
                            "time", slot.getStartTime().minusMinutes(10).toString()),
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

    // ------------------------------------------------------------------ 확정 후 취소 처리 (PRD 15, STA-07)

    /** 취소석 제안 응답 기한: 최대 6시간, 행사 2시간 전까지 */
    static final long OFFER_HOURS = 6;

    /**
     * 확정 테이블에서 한 명이 빠진 뒤 호출.
     * 1) 조건에 맞는 대기자(같은 회차를 희망했던 매칭 실패자) 한 명에게 참가 의사를 묻는다 (응답 기한 후 다음 대기자)
     * 2) 4명 미만인데 물어볼 대기자도 없으면 남은 참가자를 같은 회차의 다른 테이블로 자동 재배치, 안 되면 대체 일정 안내
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
        boolean offering = !eventStarted && seated.size() < policy.getMaxTableSize() && offerNext(table, slot, seated, policy);
        if (seated.size() >= policy.getMinTableSize()) {
            rescore(table, seated, policy);
            chatService.postSystem(tableId, "참가자 구성이 변경되어 현재 " + seated.size() + "명이 함께해요.");
            return;
        }
        if (eventStarted) {
            return; // 행사 시작 이후에는 그대로 진행
        }
        if (offering || seatOfferRepository.existsByTableIdAndStatus(tableId, SeatOffer.Status.PENDING)) {
            chatService.postSystem(tableId, "참가자 이탈로 대기자에게 참가 의사를 확인하고 있어요. 결과가 나오면 바로 알려드릴게요.");
            return;
        }
        dissolve(table, slot, seated, policy, "참가자 이탈로 테이블 인원이 " + policy.getMinTableSize()
                + "명 미만이 되어 다른 테이블로 자동 재배치합니다. 단체방은 종료돼요.", "SYSTEM");
    }

    /** 조건에 맞는 다음 대기자에게 취소석을 제안한다. 이미 응답을 기다리는 제안이 있으면 그대로 둔다. */
    private boolean offerNext(DiningTable table, EventSlot slot, List<Application> seated, MatchingPolicy policy) {
        if (seatOfferRepository.existsByTableIdAndStatus(table.getId(), SeatOffer.Status.PENDING)) {
            return true;
        }
        LocalDateTime now = clock.now();
        LocalDateTime deadline = now.plusHours(OFFER_HOURS);
        if (slot.startAt().minusHours(2).isBefore(deadline)) {
            deadline = slot.startAt().minusHours(2);
        }
        if (!deadline.isAfter(now)) {
            return false;
        }
        Set<Long> asked = seatOfferRepository.findByTableId(table.getId()).stream().map(SeatOffer::getApplicationId).collect(Collectors.toSet());
        List<Application> waitlist = applicationRepository.findByMatchStatus(MatchStatus.ALTERNATIVE_OFFERED).stream()
                .filter(a -> a.getAppStatus() == ApplicationStatus.SUBMITTED)
                .filter(a -> a.getSlotIds().contains(slot.getId()))
                .filter(a -> a.getPaymentStatus() == PaymentStatus.REFUND_AVAILABLE || a.getPaymentStatus() == PaymentStatus.PAID)
                .filter(a -> !asked.contains(a.getId()))
                .sorted(Comparator.comparing(Application::getFailedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (Application w : waitlist) {
            if (fits(w, table, slot, seated, policy)) {
                seatOfferRepository.save(new SeatOffer(table.getId(), slot.getId(), w.getId(), w.getUserId(), now, deadline));
                notificationService.notify(w.getUserId(), NotificationType.SEAT_OFFER,
                        NotificationService.vars("slot", slot.label(), "deadline", com.wassup.dinner.payment.application.PaymentService.at(deadline)),
                        "seat:" + table.getId() + ":app:" + w.getId(), w.getId(), table.getId());
                return true;
            }
        }
        return false;
    }

    /** 대기자가 이 테이블 필수 조건(인원·나이·제외 관계)과 최소 점수를 충족하는지 */
    private boolean fits(Application w, DiningTable table, EventSlot slot, List<Application> seated, MatchingPolicy policy) {
        if (seated.size() >= policy.getMaxTableSize()) {
            return false;
        }
        Set<Long> userIds = new HashSet<>();
        seated.forEach(a -> userIds.add(a.getUserId()));
        userIds.add(w.getUserId());
        Map<Long, User> users = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        TableScorer scorer = new TableScorer(relationshipService.context(policy, userIds));
        List<Candidate> members = seated.stream()
                .map(a -> RelationshipService.toCandidate(a, users.get(a.getUserId()), List.of(slot.getId()), null))
                .collect(Collectors.toCollection(ArrayList::new));
        Candidate c = RelationshipService.toCandidate(w, users.get(w.getUserId()), List.of(slot.getId()), null);
        List<Candidate> trial = new ArrayList<>(members);
        trial.add(c);
        return scorer.canAdd(members, c) && scorer.score(trial).total() >= policy.getMinScore();
    }

    /** 참가자의 취소석 제안 응답 */
    @Transactional
    public void respondSeatOffer(Long userId, Long applicationId, boolean accept) {
        SeatOffer offer = seatOfferRepository.findFirstByApplicationIdAndStatus(applicationId, SeatOffer.Status.PENDING)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "응답할 취소석 제안이 없어요."));
        LocalDateTime now = clock.now();
        DiningTable table = tableRepository.findById(offer.getTableId()).orElseThrow();
        EventSlot slot = slotRepository.findById(offer.getSlotId()).orElseThrow();
        MatchingPolicy policy = policyService.get();
        offer.setRespondedAt(now);
        if (now.isAfter(offer.getExpiresAt())) {
            offer.setStatus(SeatOffer.Status.EXPIRED);
            afterOfferClosed(table, slot, policy);
            throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "응답 기한이 지났어요.");
        }
        Application w = applicationRepository.findById(applicationId).orElseThrow();
        List<Application> seated = seated(table.getId());
        if (!accept || table.getStatus() != DiningTable.Status.CONFIRMED || !fits(w, table, slot, seated, policy)) {
            offer.setStatus(accept ? SeatOffer.Status.CANCELED : SeatOffer.Status.DECLINED);
            afterOfferClosed(table, slot, policy);
            if (accept) {
                throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "그사이 자리가 마감되었어요.");
            }
            return;
        }
        offer.setStatus(SeatOffer.Status.ACCEPTED);
        seat(w, table, slot, "취소석 제안 수락: " + slot.label() + " 테이블 " + table.getTableCode(), "USER:" + userId);
        seated = seated(table.getId());
        rescore(table, seated, policy);
        // 아직 자리가 남으면 다음 대기자에게도 묻는다
        if (seated.size() < policy.getMaxTableSize()) {
            offerNext(table, slot, seated, policy);
        }
    }

    /** 거절·만료 후: 다음 대기자에게 묻고, 더 물어볼 사람이 없는데 인원이 모자라면 해제 */
    private void afterOfferClosed(DiningTable table, EventSlot slot, MatchingPolicy policy) {
        if (table.getStatus() != DiningTable.Status.CONFIRMED) {
            return;
        }
        List<Application> seated = seated(table.getId());
        boolean eventStarted = !clock.now().isBefore(slot.startAt());
        if (!eventStarted && offerNext(table, slot, seated, policy)) {
            return;
        }
        if (!eventStarted && seated.size() < policy.getMinTableSize()) {
            dissolve(table, slot, seated, policy, "대기자 충원이 되지 않아 테이블 인원이 " + policy.getMinTableSize()
                    + "명 미만이에요. 다른 테이블로 자동 재배치합니다. 단체방은 종료돼요.", "SYSTEM");
        }
    }

    /** 응답 기한이 지난 취소석 제안 정리 (자동화 루프) */
    @Transactional
    public int expireSeatOffers() {
        int n = 0;
        MatchingPolicy policy = policyService.get();
        for (SeatOffer o : seatOfferRepository.findByStatusAndExpiresAtLessThanEqual(SeatOffer.Status.PENDING, clock.now())) {
            o.setStatus(SeatOffer.Status.EXPIRED);
            n++;
            DiningTable table = tableRepository.findById(o.getTableId()).orElse(null);
            EventSlot slot = slotRepository.findById(o.getSlotId()).orElse(null);
            if (table != null && slot != null) {
                afterOfferClosed(table, slot, policy);
            }
        }
        return n;
    }

    /** 참가자의 진행 중 취소석 제안 (앱 표시용) */
    public Optional<SeatOffer> pendingOffer(Long applicationId) {
        return seatOfferRepository.findFirstByApplicationIdAndStatus(applicationId, SeatOffer.Status.PENDING);
    }

    private void seat(Application w, DiningTable table, EventSlot slot, String reason, String actor) {
        w.setTableId(table.getId());
        w.setAssignedSlotId(slot.getId());
        w.setConfirmedAt(clock.now());
        w.setReallocationCount(w.getReallocationCount() + 1);
        if (w.getPaymentStatus() != PaymentStatus.PAID) {
            states.payment(w, PaymentStatus.PAID, "취소석 합류로 결제 유지", actor);
        }
        states.match(w, MatchStatus.CONFIRMED, reason, actor);
        states.attendance(w, AttendanceStatus.SCHEDULED, null, actor);
        resolutionService.closeAsRefilled(w.getId(), slot.getId());
        notificationService.notify(w.getUserId(), NotificationType.MATCH_CONFIRMED,
                NotificationService.vars("slot", slot.label(), "venue", "단체방 확인", "time", slot.getStartTime().minusMinutes(10).toString()),
                "app:" + w.getId() + ":seat:" + table.getId(), w.getId(), table.getId());
        chatService.postSystem(table.getId(), "새로운 참가자 1명이 합류했어요. 반갑게 맞아주세요!");
    }

    // ------------------------------------------------------------------ 운영자 긴급 수정 (MAT-14)

    /**
     * 확정 테이블의 참가자를 같은 회차의 다른 테이블로 옮긴다. targetTableId 가 null 이면 새 테이블을 만들어 옮긴다(분리).
     * 테이블 전원을 다른 테이블로 옮기면 병합이 된다. 모든 변경은 신청 이력에 운영자로 남는다.
     */
    @Transactional
    public DiningTable moveMember(Long applicationId, Long targetTableId, String reason, String actor) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("신청"));
        if (!a.isSeated() || a.getTableId() == null) {
            throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "확정 테이블에 앉은 참가자만 옮길 수 있어요.");
        }
        DiningTable source = tableRepository.findById(a.getTableId()).orElseThrow();
        EventSlot slot = slotRepository.findById(source.getSlotId()).orElseThrow();
        MatchingPolicy policy = policyService.get();
        DiningTable target;
        if (targetTableId == null) {
            target = tableRepository.save(new DiningTable(slot.getId(), source.getMatchRunId(), nextCode(slot.getId(), 0), "운영자 분리", clock.now()));
        } else {
            target = tableRepository.findById(targetTableId).orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("테이블"));
            if (!target.getSlotId().equals(slot.getId()) || target.getStatus() != DiningTable.Status.CONFIRMED) {
                throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "같은 회차의 확정 테이블로만 옮길 수 있어요.");
            }
            if (target.getId().equals(source.getId())) {
                throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "같은 테이블이에요.");
            }
            if (seated(target.getId()).size() >= policy.getMaxTableSize()) {
                throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "옮길 테이블이 이미 " + policy.getMaxTableSize() + "명이에요.");
            }
        }
        String why = reason == null || reason.isBlank() ? "운영자 조정" : reason.trim();
        a.setTableId(target.getId());
        a.setReallocationCount(a.getReallocationCount() + 1);
        states.match(a, MatchStatus.CONFIRMED, "운영자 이동: 테이블 " + source.getTableCode() + " → " + target.getTableCode() + " (" + why + ")", actor);
        notificationService.notify(a.getUserId(), NotificationType.TABLE_CHANGED,
                NotificationService.vars("reason", slot.label() + " 테이블 " + target.getTableCode() + "(으)로 옮겨졌어요. 단체방을 확인해 주세요."),
                "app:" + a.getId() + ":admin-move:" + target.getId() + ":" + clock.now(), a.getId(), target.getId());

        List<Application> targetSeated = seated(target.getId());
        rescore(target, targetSeated, policy);
        if (targetTableId == null) {
            // 새 테이블: 식당 배정 + 단체방 생성
            Map<Long, User> users = userRepository.findAllById(targetSeated.stream().map(Application::getUserId).toList()).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            Optional<Venue> venue = venueService.assign(target, slot, targetSeated.stream().map(x -> users.get(x.getUserId()).getProfile().getPriceRanges()).toList());
            chatService.createForTable(target, slot, venue.orElse(null));
        } else {
            chatService.postSystem(target.getId(), "운영진이 테이블 구성을 조정했어요. 새로운 참가자 1명이 합류했어요.");
        }

        List<Application> left = seated(source.getId());
        if (left.isEmpty()) {
            source.setStatus(DiningTable.Status.DISSOLVED);
            source.setDissolvedAt(clock.now());
            venueService.release(source);
            chatService.close(source.getId(), "운영진이 테이블을 다른 테이블과 합쳤어요. 이 단체방은 종료돼요.");
        } else {
            rescore(source, left, policy);
            chatService.postSystem(source.getId(), "운영진이 테이블 구성을 조정했어요. 현재 " + left.size() + "명이 함께해요.");
        }
        return target;
    }

    /** 운영자 테이블 해제: 남은 참가자는 같은 회차 다른 테이블로 재배치, 안 되면 대체 일정 안내 */
    @Transactional
    public void dissolveByAdmin(Long tableId, String reason, String actor) {
        DiningTable table = tableRepository.findById(tableId).orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("테이블"));
        if (table.getStatus() != DiningTable.Status.CONFIRMED) {
            throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE, "확정 테이블만 해제할 수 있어요.");
        }
        EventSlot slot = slotRepository.findById(table.getSlotId()).orElseThrow();
        String why = reason == null || reason.isBlank() ? "운영 사유" : reason.trim();
        dissolve(table, slot, seated(tableId), policyService.get(), "운영진이 테이블을 해제했어요 (" + why + "). 다른 테이블로 재배치하거나 대체 일정을 안내드릴게요. 단체방은 종료돼요.", actor);
    }

    private void dissolve(DiningTable table, EventSlot slot, List<Application> remaining, MatchingPolicy policy, String notice, String actor) {
        table.setStatus(DiningTable.Status.DISSOLVED);
        table.setDissolvedAt(clock.now());
        venueService.release(table);
        seatOfferRepository.findByTableId(table.getId()).stream().filter(o -> o.getStatus() == SeatOffer.Status.PENDING)
                .forEach(o -> o.setStatus(SeatOffer.Status.CANCELED));
        chatService.close(table.getId(), notice);

        // 같은 회차의 다른 확정 테이블로 재배치
        List<DiningTable> others = tableRepository.findBySlotIdAndStatus(slot.getId(), DiningTable.Status.CONFIRMED);
        for (Application a : remaining) {
            states.match(a, MatchStatus.REALLOCATING, "테이블 해제로 자동 재배치 중", actor);
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
                resolutionService.offer(a, "함께하던 테이블이 해제되었고, 같은 회차에 합류할 수 있는 테이블이 없었어요.");
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
