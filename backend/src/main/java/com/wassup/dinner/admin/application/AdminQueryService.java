package com.wassup.dinner.admin.application;

import com.wassup.dinner.admin.application.AdminDtos.*;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.chat.domain.ChatMessageRepository;
import com.wassup.dinner.chat.domain.ChatRoom;
import com.wassup.dinner.chat.domain.ChatRoomRepository;
import com.wassup.dinner.chat.domain.ScheduledRoomPost;
import com.wassup.dinner.chat.domain.ScheduledRoomPostRepository;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.matching.application.MatchingPolicyService;
import com.wassup.dinner.matching.application.RelationshipService;
import com.wassup.dinner.matching.domain.*;
import com.wassup.dinner.matching.engine.Candidate;
import com.wassup.dinner.matching.engine.MatchContext;
import com.wassup.dinner.matching.engine.PairKey;
import com.wassup.dinner.matching.engine.ScoreBreakdown;
import com.wassup.dinner.matching.engine.TableScorer;
import com.wassup.dinner.notification.domain.Notification;
import com.wassup.dinner.notification.domain.NotificationRepository;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.ops.domain.ExceptionCase;
import com.wassup.dinner.ops.domain.ExceptionCaseRepository;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.payment.domain.Refund;
import com.wassup.dinner.payment.domain.RefundRepository;
import com.wassup.dinner.safety.domain.SafetyReport;
import com.wassup.dinner.safety.domain.SafetyReportRepository;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final EventSlotRepository slotRepository;
    private final DiningTableRepository tableRepository;
    private final MatchRunRepository runRepository;
    private final VenueRepository venueRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ScheduledRoomPostRepository postRepository;
    private final NotificationRepository notificationRepository;
    private final ExceptionCaseRepository exceptionRepository;
    private final RefundRepository refundRepository;
    private final MatchResolutionRepository resolutionRepository;
    private final FeedbackRepository feedbackRepository;
    private final SafetyReportRepository reportRepository;
    private final RelationshipService relationshipService;
    private final MatchingPolicyService policyService;
    private final AppClock clock;

    // ------------------------------------------------------------------ 대시보드

    public Dashboard dashboard() {
        List<Application> apps = applicationRepository.findAll();
        List<DiningTable> tables = tableRepository.findAll();
        List<ExceptionCase> openEx = exceptionRepository.findByStatusOrderByIdDesc(ExceptionCase.Status.OPEN);
        List<Refund> refunds = refundRepository.findAll();

        long total = apps.size();
        long paid = apps.stream().filter(a -> a.getPaidAt() != null).count();
        long confirmed = apps.stream().filter(a -> a.getTableId() != null).count();
        long failed = apps.stream().filter(a -> a.getFailedAt() != null).count();
        long reallocAttempts = apps.stream().filter(a -> a.getReallocationCount() > 0).count();
        long reallocSuccess = apps.stream().filter(a -> a.getReallocationCount() > 0 && a.getTableId() != null).count();
        long offered = resolutionRepository.count();
        long transferred = apps.stream().filter(a -> a.getMatchStatus() == MatchStatus.TRANSFERRED).count();
        long refundsDone = refunds.stream().filter(r -> r.getStatus() == Refund.Status.COMPLETED).count();
        long autoRefund = refunds.stream().filter(r -> r.getStatus() == Refund.Status.COMPLETED && r.getAttempts() == 1).count();
        List<Application> settled = apps.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED
                || a.getAttendanceStatus() == AttendanceStatus.NO_SHOW).toList();
        long attended = settled.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).count();
        long noShow = settled.size() - attended;
        long feedback = feedbackRepository.count();
        Set<Long> attendedUsers = settled.stream().filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED)
                .map(Application::getUserId).collect(Collectors.toSet());
        long revisit = attendedUsers.stream().filter(u -> apps.stream().filter(a -> a.getUserId().equals(u)
                && a.getTransferredFromId() == null).count() >= 2).count();
        long exceptionsTotal = exceptionRepository.count();
        long autoTables = tables.stream().filter(DiningTable::isAutoConfirmed).count();

        Kpi kpi = new Kpi(total, paid, pct(paid, total), confirmed, pct(confirmed, paid), tables.size(),
                pct(autoTables, tables.size()), reallocAttempts, pct(reallocSuccess, reallocAttempts),
                pct(transferred, offered), pct(autoRefund, refunds.size()), pct(attended, confirmed),
                pct(noShow, settled.size()), pct(feedback, attended), pct(revisit, attendedUsers.size()),
                pct(exceptionsTotal, total));

        Pending pending = new Pending(
                resolutionRepository.countByStatus(MatchResolution.Status.PENDING),
                countType(openEx, ExceptionType.UNRESOLVED_CONFLICT, ExceptionType.DATA_MISSING),
                tables.stream().filter(t -> t.getStatus() == DiningTable.Status.CONFIRMED && t.getVenueId() == null).count(),
                notificationRepository.countByStatus(Notification.Status.FAILED) + postRepository.countByStatus(ScheduledRoomPost.Status.FAILED),
                refundRepository.countByStatus(Refund.Status.FAILED),
                countType(openEx, ExceptionType.NO_SHOW_REVIEW, ExceptionType.LATE_CANCEL),
                reportRepository.countByStatus(SafetyReport.Status.OPEN),
                openEx.size());

        List<SlotSummary> slots = slotRepository.findAllByOrderByDateDescStartTimeAscRegionAsc().stream().map(s -> {
            List<Application> related = apps.stream().filter(a -> a.getSlotIds().contains(s.getId())).toList();
            return new SlotSummary(s.getId(), s.label(), s.getStatus().name(), s.getMatchAt(), related.size(),
                    related.stream().filter(a -> a.getPaidAt() != null).count(),
                    tables.stream().filter(t -> t.getSlotId().equals(s.getId()) && t.getStatus() != DiningTable.Status.DISSOLVED).count(),
                    apps.stream().filter(a -> s.getId().equals(a.getAssignedSlotId()) && a.isSeated()).count(),
                    related.stream().filter(a -> a.getMatchStatus() == MatchStatus.ALTERNATIVE_OFFERED
                            || a.getMatchStatus() == MatchStatus.NO_MATCH).count());
        }).toList();
        return new Dashboard(kpi, pending, slots, clock.now());
    }

    // ------------------------------------------------------------------ 신청자

    public List<ApplicantRow> applicants(Long slotId, MatchStatus matchStatus, PaymentStatus paymentStatus, String q) {
        List<Application> apps = applicationRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> slotId == null || a.getSlotIds().contains(slotId))
                .filter(a -> matchStatus == null || a.getMatchStatus() == matchStatus)
                .filter(a -> paymentStatus == null || a.getPaymentStatus() == paymentStatus)
                .toList();
        List<ApplicantRow> rows = toRows(apps);
        if (q == null || q.isBlank()) {
            return rows;
        }
        return rows.stream().filter(r -> (r.name() != null && r.name().contains(q)) || r.phone().contains(q)).toList();
    }

    public List<ApplicantRow> toRows(List<Application> apps) {
        Map<Long, User> users = userRepository.findAllById(apps.stream().map(Application::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, EventSlot> slots = slotRepository.findAll().stream().collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        Map<Long, DiningTable> tables = tableRepository.findAllById(apps.stream().map(Application::getTableId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream().collect(Collectors.toMap(DiningTable::getId, Function.identity()));
        List<Application> all = applicationRepository.findAll();
        List<SafetyReport> reports = reportRepository.findAll();
        return apps.stream().map(a -> {
            User u = users.get(a.getUserId());
            DiningTable t = a.getTableId() == null ? null : tables.get(a.getTableId());
            long attended = all.stream().filter(x -> x.getUserId().equals(a.getUserId()) && x.getAttendanceStatus() == AttendanceStatus.ATTENDED).count();
            long noShows = all.stream().filter(x -> x.getUserId().equals(a.getUserId()) && x.getAttendanceStatus() == AttendanceStatus.NO_SHOW).count();
            long reported = reports.stream().filter(r -> r.getReportedId().equals(a.getUserId())).count();
            return new ApplicantRow(a.getId(), a.getUserId(), u.getName(), u.getPhone(), u.getProfile().getInstagramId(),
                    u.getProfile().getBirthYear(), u.getProfile().getGender() == null ? null : u.getProfile().getGender().getLabel(),
                    u.getProfile().getJob(), u.getProfile().getMbti(),
                    a.getSlotIds().stream().map(slots::get).filter(Objects::nonNull).map(EventSlot::label).toList(),
                    a.getOtherRegion(), a.getProduct() == null ? null : a.getProduct().getLabel(),
                    a.getAppStatus().name(), a.getPaymentStatus().name(), a.getMatchStatus().name(),
                    a.getAttendanceStatus() == null ? null : a.getAttendanceStatus().name(),
                    a.getTableId(), t == null ? null : t.getTableCode(), a.getReallocationCount(), a.getStatusReason(),
                    attended, noShows, reported, a.getSubmittedAt());
        }).toList();
    }

    // ------------------------------------------------------------------ 테이블

    public List<TableRow> tables(Long slotId) {
        List<DiningTable> list = slotId == null ? tableRepository.findAllByOrderByIdDesc() : tableRepository.findBySlotIdOrderByIdAsc(slotId);
        return tableRows(list);
    }

    private List<TableRow> tableRows(List<DiningTable> list) {
        Map<Long, EventSlot> slots = slotRepository.findAll().stream().collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        Map<Long, Venue> venues = venueRepository.findAll().stream().collect(Collectors.toMap(Venue::getId, Function.identity()));
        return list.stream().map(t -> new TableRow(t.getId(), t.getTableCode(), t.getSlotId(), slots.get(t.getSlotId()).label(),
                t.getStatus().name(), t.getMemberCount(), t.getTotalScore(), t.ageGap(), t.getMaleCount(), t.getFemaleCount(),
                t.getVenueId() == null ? null : venues.get(t.getVenueId()).getName(),
                t.getVenueId() == null && t.getStatus() == DiningTable.Status.CONFIRMED,
                t.getFormation(), t.getConfirmedAt())).toList();
    }

    /** 테이블 상세: PRD 7.2 매칭 결과 표시 항목 전체 */
    public TableDetail table(Long tableId) {
        DiningTable t = tableRepository.findById(tableId).orElseThrow(() -> ApiException.notFound("테이블"));
        EventSlot slot = slotRepository.findById(t.getSlotId()).orElseThrow();
        List<Application> members = applicationRepository.findByTableId(tableId);
        List<Application> seated = members.stream().filter(Application::isSeated).toList();
        Map<Long, User> users = userRepository.findAllById(members.stream().map(Application::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        MatchingPolicy policy = policyService.get();
        Set<Long> seatedUserIds = seated.stream().map(Application::getUserId).collect(Collectors.toSet());
        MatchContext ctx = relationshipService.context(policy, seatedUserIds);
        TableScorer scorer = new TableScorer(ctx);
        List<Candidate> candidates = seated.stream()
                .map(a -> RelationshipService.toCandidate(a, users.get(a.getUserId()), a.getSlotIds(), null)).toList();
        ScoreBreakdown sb = candidates.isEmpty() ? null : scorer.score(candidates);
        boolean conflict = false;
        for (Long x : seatedUserIds) {
            for (Long y : seatedUserIds) {
                if (x < y && ctx.excluded(x, y)) conflict = true;
            }
        }
        // 확정 시점 이전의 만남만 '이전 만남'으로 본다
        Set<PairKey> metBefore = relationshipService.metBefore(seatedUserIds);

        Map<NotificationType, List<Notification>> confirmNotes = notificationRepository.findByUserIdInOrderByIdDesc(seatedUserIds)
                .stream().filter(n -> tableId.equals(n.getTableId())).collect(Collectors.groupingBy(Notification::getType));
        int year = clock.today().getYear();
        List<MemberDetail> details = members.stream().map(a -> {
            User u = users.get(a.getUserId());
            List<String> metWith = seated.stream().filter(o -> !o.getUserId().equals(a.getUserId()))
                    .filter(o -> metBefore.contains(PairKey.of(a.getUserId(), o.getUserId())) && pastTogether(a.getUserId(), o.getUserId(), tableId))
                    .map(o -> users.get(o.getUserId()).getName()).toList();
            String noteStatus = confirmNotes.getOrDefault(NotificationType.MATCH_CONFIRMED, List.of()).stream()
                    .filter(n -> n.getUserId().equals(a.getUserId())).map(n -> n.getStatus().name()).findFirst().orElse(null);
            return new MemberDetail(a.getId(), u.getId(), u.getName(), u.getPhone(), u.ageAt(year) - 1,
                    u.getProfile().getGender().getLabel(), u.getProfile().getMbti(), u.getProfile().getJob(),
                    labels(u.getProfile().getInterests(), Options.Interest.class), labels(u.getProfile().getTraits(), Options.Trait.class),
                    labels(u.getProfile().getDesiredTypes(), Options.DesiredType.class), labels(u.getProfile().getPriceRanges(), Options.PriceRange.class),
                    u.getProfile().getFoodRestrictions(), a.getSlotIds().contains(t.getSlotId()),
                    sb != null && Boolean.TRUE.equals(sb.desireSatisfied().get(a.getId())), metWith,
                    a.getReallocationCount(), a.getStatusReason(), a.getMatchStatus().name(),
                    a.getAttendanceStatus() == null ? null : a.getAttendanceStatus().name(), noteStatus);
        }).toList();

        Venue v = t.getVenueId() == null ? null : venueRepository.findById(t.getVenueId()).orElse(null);
        Optional<ChatRoom> room = roomRepository.findByTableId(tableId);
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("total", t.getTotalScore());
        scores.put("age", t.getAgeScore());
        scores.put("gender", t.getGenderScore());
        scores.put("mbti", t.getMbtiScore());
        scores.put("interest", t.getInterestScore());
        scores.put("desire", t.getDesireScore());
        scores.put("newness", t.getNewnessScore());
        return new TableDetail(t.getId(), t.getTableCode(), slot.getId(), slot.label(), t.getStatus().name(), t.getFormation(),
                t.isAutoConfirmed(), t.getMemberCount(), scores, t.getMinBirthYear(), t.getMaxBirthYear(), t.ageGap(),
                t.getMaleCount(), t.getFemaleCount(), t.getMbtiSummary(), new ArrayList<>(t.getCommonInterests()),
                t.getMetBeforePairs(), conflict, t.getReallocatedMembers(), t.getCuration(), t.getConfirmedAt(),
                v == null ? null : new VenueSummary(v.getId(), v.getName(), v.getAddress(), t.getReservationName(), v.getPriceRange().getLabel()),
                room.map(ChatRoom::getId).orElse(null), room.map(r -> messageRepository.countByRoomId(r.getId())).orElse(0L),
                postRepository.findByTableId(tableId).stream().map(p -> new PostStatus(p.getKind().name(), p.getScheduledAt(),
                        p.getStatus().name(), p.getAttempts(), p.getLastError())).toList(),
                details, t.getMatchRunId());
    }

    /** 이 테이블이 아닌 다른 테이블에서 함께였는지 */
    private boolean pastTogether(Long u1, Long u2, Long currentTableId) {
        Set<Long> t1 = applicationRepository.findByUserIdAndTableIdIsNotNullOrderByIdDesc(u1).stream()
                .map(Application::getTableId).filter(id -> !id.equals(currentTableId)).collect(Collectors.toSet());
        return applicationRepository.findByUserIdAndTableIdIsNotNullOrderByIdDesc(u2).stream()
                .map(Application::getTableId).anyMatch(t1::contains);
    }

    // ------------------------------------------------------------------ 매칭 실행

    public List<MatchRunDetail> runs() {
        return runRepository.findAllByOrderByIdDesc().stream().map(r -> runDetail(r, false)).toList();
    }

    public MatchRunDetail run(Long id) {
        return runDetail(runRepository.findById(id).orElseThrow(() -> ApiException.notFound("매칭 실행")), true);
    }

    private MatchRunDetail runDetail(MatchRun r, boolean full) {
        List<String> slotLabels = slotRepository.findAllById(r.getSlotIds()).stream().map(EventSlot::label).toList();
        List<TableRow> tables = full ? tableRows(tableRepository.findByMatchRunId(r.getId())) : List.of();
        List<ApplicantRow> failed = full ? toRows(applicationRepository.findAll().stream()
                .filter(a -> a.getFailedAt() != null && a.getSlotIds().stream().anyMatch(r.getSlotIds()::contains)).toList()) : List.of();
        List<String> log = full && r.getLog() != null ? List.of(r.getLog().split("\n")) : List.of();
        return new MatchRunDetail(r.getId(), slotLabels, r.getTriggerType().name(), r.getAlgorithmVersion(), r.getPolicySnapshot(),
                r.getCandidateCount(), r.getExcludedCount(), r.getInitialTables(), r.getSplitCount(), r.getMergeCount(),
                r.getReallocatedCount(), r.getDissolvedCount(), r.getConfirmedTables(), r.getConfirmedParticipants(),
                r.getFailedCount(), r.getStartedAt(), r.getFinishedAt(), log, tables, failed);
    }

    // ------------------------------------------------------------------ 참석·환불

    public List<AttendanceRow> attendance(Long slotId) {
        Map<Long, EventSlot> slots = slotRepository.findAll().stream().collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        List<Application> apps = applicationRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> a.getAttendanceStatus() != null)
                .filter(a -> slotId == null || slotId.equals(a.getAssignedSlotId())).toList();
        Map<Long, User> users = userRepository.findAllById(apps.stream().map(Application::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> codes = tableRepository.findAllById(apps.stream().map(Application::getTableId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DiningTable::getId, DiningTable::getTableCode));
        Set<Long> openReview = exceptionRepository.findByStatusOrderByIdDesc(ExceptionCase.Status.OPEN).stream()
                .filter(e -> e.getType() == ExceptionType.NO_SHOW_REVIEW || e.getType() == ExceptionType.LATE_CANCEL)
                .map(ExceptionCase::getApplicationId).filter(Objects::nonNull).collect(Collectors.toSet());
        return apps.stream().map(a -> new AttendanceRow(a.getId(), a.getUserId(), users.get(a.getUserId()).getName(),
                users.get(a.getUserId()).getPhone(), a.getAssignedSlotId() == null ? null : slots.get(a.getAssignedSlotId()).label(),
                a.getTableId() == null ? null : codes.get(a.getTableId()), a.getAttendanceStatus().name(),
                a.getCheckedInAt(), a.getCanceledAt(), openReview.contains(a.getId()))).toList();
    }

    public List<RefundRow> refunds() {
        List<Refund> refunds = refundRepository.findAllByOrderByIdDesc();
        Map<Long, User> users = userRepository.findAllById(refunds.stream().map(Refund::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return refunds.stream().map(r -> new RefundRow(r.getId(), r.getApplicationId(), r.getUserId(),
                users.get(r.getUserId()).getName(), r.getAmount(), r.getStatus().name(), r.getAttempts(),
                r.getFailureReason(), r.getReason(), r.getRequestedAt(), r.getCompletedAt())).toList();
    }

    // ------------------------------------------------------------------ 보조

    private static <E extends Enum<E> & Options.Labeled> List<String> labels(Collection<String> codes, Class<E> type) {
        return codes.stream().map(c -> {
            try {
                return Enum.valueOf(type, c).getLabel();
            } catch (IllegalArgumentException e) {
                return c;
            }
        }).toList();
    }

    private static long countType(List<ExceptionCase> list, ExceptionType... types) {
        Set<ExceptionType> set = Set.of(types);
        return list.stream().filter(e -> set.contains(e.getType())).count();
    }

    private static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(1000.0 * part / whole) / 10.0;
    }
}
