package com.wassup.dinner.matching.application;

import com.wassup.dinner.application.application.ResolutionService;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.matching.domain.MatchRunRepository;
import com.wassup.dinner.matching.domain.MatchingPolicy;
import com.wassup.dinner.matching.engine.*;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.slot.domain.SlotStatus;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 자동 매칭 실행 (MAT-01 ~ MAT-13).
 * 마감 시각이 된 회차를 원자적으로 선점(RECRUITING → MATCHING)하므로
 * 스케줄러·운영자 수동 실행이 겹쳐도 같은 회차가 두 번 매칭되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final EventSlotRepository slotRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final DiningTableRepository tableRepository;
    private final MatchRunRepository runRepository;
    private final ApplicationStates states;
    private final MatchingPolicyService policyService;
    private final RelationshipService relationshipService;
    private final TableService tableService;
    private final ResolutionService resolutionService;
    private final NotificationService notificationService;
    private final ExceptionCaseService exceptionCaseService;
    private final AppClock clock;

    /** 매칭 실행 시각이 지난 모든 모집 중 회차를 한 번에 매칭 (회차 간 재배치를 위해 묶어서 실행) */
    @Transactional
    public Optional<MatchRun> runDue(MatchRun.Trigger trigger) {
        List<Long> due = slotRepository.findByStatusAndMatchAtLessThanEqual(SlotStatus.RECRUITING, clock.now())
                .stream().map(EventSlot::getId).toList();
        return due.isEmpty() ? Optional.empty() : run(due, trigger);
    }

    @Transactional
    public Optional<MatchRun> run(Collection<Long> requestedSlotIds, MatchRun.Trigger trigger) {
        List<Long> claimed = requestedSlotIds.stream().distinct().sorted()
                .filter(id -> slotRepository.claimForMatching(id) == 1).toList();
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = clock.now();
        Map<Long, EventSlot> slots = slotRepository.findAllById(claimed).stream()
                .collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        MatchingPolicy policy = policyService.get();
        MatchRun run = runRepository.save(new MatchRun(claimed, trigger, MatchingEngine.VERSION, now));
        run.setPolicySnapshot("weights(age=%s,gender=%s,mbti=%s,interest=%s,desire=%s,newness=%s) minScore=%s maxAgeGap=%d"
                .formatted(policy.getAgeWeight(), policy.getGenderWeight(), policy.getMbtiWeight(), policy.getInterestWeight(),
                        policy.getDesireWeight(), policy.getNewnessWeight(), policy.getMinScore(), policy.getMaxAgeGap()));
        List<String> runLog = new ArrayList<>();

        // ---- 1. 후보 수집: 결제 완료 + 유효 신청만
        Set<Long> claimedSet = Set.copyOf(claimed);
        List<Application> waiting = applicationRepository.findByAppStatusAndMatchStatus(ApplicationStatus.SUBMITTED, MatchStatus.WAITING)
                .stream().filter(a -> a.getSlotIds().stream().anyMatch(claimedSet::contains)).toList();
        Map<Long, User> users = userRepository.findAllById(waiting.stream().map(Application::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Application> eligible = new ArrayList<>();
        Map<Long, List<Long>> dueOptions = new HashMap<>();
        int excluded = 0;
        for (Application a : waiting) {
            List<Long> options = a.getSlotIds().stream().filter(claimedSet::contains).toList();
            User u = users.get(a.getUserId());
            if (!a.isPaid()) {
                if (!hasFutureSlot(a, claimedSet)) {
                    states.app(a, ApplicationStatus.CANCELED, "결제 미완료로 매칭 후보에서 제외", "SYSTEM");
                    a.setCanceledAt(now);
                    notificationService.notify(a.getUserId(), NotificationType.CANCELED,
                            NotificationService.vars("reason", "결제 기한까지 결제가 완료되지 않아 신청이 취소되었어요."),
                            "app:" + a.getId(), a.getId(), null);
                }
                excluded++;
                runLog.add("[제외] 신청#" + a.getId() + " 결제 미완료");
                continue;
            }
            if (u == null || !u.isProfileComplete()) {
                states.match(a, MatchStatus.EXCEPTION, "프로필 필수 정보 누락", "SYSTEM");
                exceptionCaseService.open(ExceptionType.DATA_MISSING, "app:" + a.getId(),
                        "신청#" + a.getId() + " 프로필 필수 항목(출생연도/성별/MBTI 등)이 누락되어 매칭할 수 없습니다.",
                        ExceptionCaseService.Refs.application(a.getId(), a.getUserId()));
                excluded++;
                continue;
            }
            if (!u.canApply(clock.today())) {
                states.match(a, MatchStatus.EXCEPTION, "참가 제한 계정", "SYSTEM");
                exceptionCaseService.open(ExceptionType.UNRESOLVED_CONFLICT, "app:" + a.getId(),
                        "참가 제한 상태(" + u.getStatus() + ")인 사용자의 결제 완료 신청입니다. 환불 여부를 확인해 주세요.",
                        ExceptionCaseService.Refs.application(a.getId(), a.getUserId()));
                excluded++;
                continue;
            }
            states.match(a, MatchStatus.MATCHING, null, "SYSTEM");
            eligible.add(a);
            dueOptions.put(a.getId(), options);
        }

        // ---- 2. 엔진 실행
        Set<Long> userIds = eligible.stream().map(Application::getUserId).collect(Collectors.toSet());
        MatchContext ctx = relationshipService.context(policy, userIds);
        Map<Long, RelationshipService.Learned> learned = relationshipService.learned(userIds);
        List<Candidate> candidates = eligible.stream()
                .map(a -> RelationshipService.toCandidate(a, users.get(a.getUserId()), dueOptions.get(a.getId()), learned.get(a.getUserId())))
                .toList();
        Map<Long, SlotInfo> slotInfos = slots.values().stream().collect(Collectors.toMap(EventSlot::getId,
                s -> new SlotInfo(s.getId(), s.getDate(), s.getRegion().name(), s.label())));
        MatchResult result = new MatchingEngine(ctx).run(candidates, slotInfos);
        runLog.addAll(result.log());

        // ---- 3. 검증 통과 테이블 자동 확정 (운영자 승인 없음)
        Map<Long, Application> appById = eligible.stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        int confirmedPeople = 0;
        for (MatchResult.PlannedTable plan : result.tables()) {
            EventSlot slot = slots.get(plan.slotId());
            DiningTable table = new DiningTable(slot.getId(), run.getId(), tableService.nextCode(slot.getId(), 0), plan.formation(), now);
            tableService.applyScore(table, plan.score(), plan.members());
            table = tableRepository.save(table);

            List<Application> members = new ArrayList<>();
            int reallocated = 0;
            for (Candidate c : plan.members()) {
                Application a = appById.get(c.applicationId());
                int count = result.reallocationCount().getOrDefault(a.getId(), 0);
                if (count > 0) {
                    reallocated++;
                    states.match(a, MatchStatus.REALLOCATING, "최초 조합 실패로 자동 재배치", "SYSTEM");
                }
                a.setReallocationCount(a.getReallocationCount() + count);
                a.setTableId(table.getId());
                a.setAssignedSlotId(slot.getId());
                a.setConfirmedAt(now);
                states.match(a, MatchStatus.CONFIRMED, result.placementReason().getOrDefault(a.getId(), "자동 확정"), "SYSTEM");
                states.attendance(a, AttendanceStatus.SCHEDULED, null, "SYSTEM");
                members.add(a);
            }
            table.setReallocatedMembers(reallocated);
            confirmedPeople += members.size();
            tableService.afterConfirm(table, slot, members, users);
        }

        // ---- 4. 매칭 실패자: 다른 희망 회차 대기 or 대체 일정 안내
        for (MatchResult.Unplaced u : result.unplaced()) {
            Application a = appById.get(u.candidate().applicationId());
            if (hasFutureSlot(a, claimedSet)) {
                a.setReallocationCount(a.getReallocationCount() + 1);
                states.match(a, MatchStatus.WAITING, "이번 회차에서는 매칭되지 않아, 신청하신 다른 날짜의 자동 매칭을 기다리고 있어요. ("
                        + u.reason() + ")", "SYSTEM");
            } else {
                resolutionService.offer(a, u.reason());
            }
        }

        // ---- 5. 회차·실행 기록
        slots.values().forEach(s -> s.setStatus(SlotStatus.MATCHED));
        MatchResult.Stats st = result.stats();
        run.setCandidateCount(st.candidates());
        run.setExcludedCount(excluded);
        run.setInitialTables(st.initialTables());
        run.setSplitCount(st.splits());
        run.setMergeCount(st.merges());
        run.setReallocatedCount(st.reallocated());
        run.setDissolvedCount(st.dissolved());
        run.setConfirmedTables(st.confirmedTables());
        run.setConfirmedParticipants(confirmedPeople);
        run.setFailedCount(st.unplaced());
        run.setLog(String.join("\n", runLog));
        run.setFinishedAt(clock.now());
        log.info("MatchRun#{} slots={} candidates={} tables={} failed={}", run.getId(), claimed,
                st.candidates(), st.confirmedTables(), st.unplaced());
        return Optional.of(run);
    }

    /** 이번 실행에 포함되지 않은, 아직 모집 중인 다른 희망 회차가 있는지 */
    private boolean hasFutureSlot(Application a, Set<Long> claimed) {
        return a.getSlotIds().stream().filter(id -> !claimed.contains(id))
                .map(slotRepository::findById).flatMap(Optional::stream)
                .anyMatch(s -> s.getStatus() == SlotStatus.RECRUITING);
    }
}
