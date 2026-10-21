package com.wassup.dinner.application.application;

import com.wassup.dinner.application.application.ApplicationView.*;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.chat.domain.ChatRoom;
import com.wassup.dinner.chat.domain.ChatRoomRepository;
import com.wassup.dinner.common.audit.AuditService;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.payment.domain.Payment;
import com.wassup.dinner.payment.domain.PaymentRepository;
import com.wassup.dinner.payment.domain.Refund;
import com.wassup.dinner.payment.domain.RefundRepository;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Application → 참가자 화면용 ApplicationView 변환 (상태 문구·다음 행동 결정). */
@Component
@RequiredArgsConstructor
public class ApplicationViewAssembler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final EventSlotRepository slotRepository;
    private final DiningTableRepository tableRepository;
    private final ChatRoomRepository roomRepository;
    private final VenueRepository venueRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final MatchResolutionRepository resolutionRepository;
    private final FeedbackRepository feedbackRepository;
    private final AuditService auditService;
    private final AppClock clock;

    public ApplicationView toView(Application a, boolean withTimeline) {
        LocalDateTime now = clock.now();
        Map<Long, EventSlot> slots = new LinkedHashMap<>();
        slotRepository.findAllById(a.getSlotIds()).forEach(s -> slots.put(s.getId(), s));
        List<SlotItem> slotItems = a.getSlotIds().stream().map(slots::get).filter(Objects::nonNull)
                .map(s -> slotItem(s, s.getId().equals(a.getAssignedSlotId()))).toList();

        EventSlot assigned = a.getAssignedSlotId() == null ? null : slots.get(a.getAssignedSlotId());
        EventSlot reference = assigned != null ? assigned : slots.values().stream()
                .min(Comparator.comparing(EventSlot::startAt)).orElse(null);
        LocalDateTime announce = slots.values().stream().filter(s -> s.getStatus() == com.wassup.dinner.slot.domain.SlotStatus.RECRUITING)
                .map(EventSlot::getMatchAt).min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime refundDeadline = reference == null ? null : reference.refundDeadline();
        boolean cancelWillRefund = refundDeadline != null && now.isBefore(refundDeadline);

        PaymentInfo payment = null;
        if (a.getPaymentId() != null) {
            Payment p = paymentRepository.findById(a.getPaymentId()).orElse(null);
            if (p != null) {
                payment = new PaymentInfo(p.getId(), p.getProduct().name(), p.getProduct().getLabel(), p.getAmount(), a.getTicketId());
            }
        }

        TableInfo table = null;
        if (a.getTableId() != null && assigned != null) {
            DiningTable t = tableRepository.findById(a.getTableId()).orElse(null);
            if (t != null) {
                Venue v = t.getVenueId() == null ? null : venueRepository.findById(t.getVenueId()).orElse(null);
                Long roomId = roomRepository.findByTableId(t.getId()).map(ChatRoom::getId).orElse(null);
                boolean canCheckIn = a.isSeated() && a.getAttendanceStatus() == AttendanceStatus.SCHEDULED
                        && !now.isBefore(assigned.startAt().minusHours(1)) && now.isBefore(assigned.endAt());
                table = new TableInfo(t.getId(), t.getTableCode(), roomId, assigned.label(), assigned.startAt(),
                        t.getMemberCount(), v == null ? null : v.getName(), v == null ? null : v.getAddress(),
                        v == null ? null : v.getMapUrl(), t.getReservationName(), v == null ? null : v.getPriceGuide(),
                        t.getCuration(), canCheckIn);
            }
        }

        ResolutionInfo resolution = resolutionRepository.findByApplicationId(a.getId()).map(r -> {
            List<SlotItem> alts = slotRepository.findAllById(r.getAlternativeSlotIds()).stream()
                    .sorted(Comparator.comparing(EventSlot::startAt)).map(s -> slotItem(s, false)).toList();
            Refund refund = refundRepository.findByApplicationId(a.getId()).orElse(null);
            return new ResolutionInfo(r.getReason(), alts, r.getChoice() == null ? null : r.getChoice().name(),
                    r.getStatus().name(), r.getNewApplicationId(),
                    refund == null ? null : refund.getAmount(), refund == null ? null : refund.getStatus().name());
        }).orElse(null);

        boolean feedbackSubmitted = feedbackRepository.existsByApplicationId(a.getId());
        String[] text = statusText(a, announce, table, feedbackSubmitted);
        List<String> actions = actions(a, now, assigned, table, feedbackSubmitted, resolution);
        List<TimelineItem> timeline = withTimeline
                ? auditService.historyOf(ApplicationStates.ENTITY, a.getId()).stream().map(TimelineItem::of).toList()
                : List.of();

        return new ApplicationView(a.getId(), a.getAppStatus().name(), a.getPaymentStatus().name(), a.getMatchStatus().name(),
                a.getAttendanceStatus() == null ? null : a.getAttendanceStatus().name(), text[0], text[1], actions,
                slotItems, a.getOtherRegion(), payment, announce, refundDeadline, cancelWillRefund,
                a.getReallocationCount(), a.getStatusReason(), table, resolution, a.getTransferredToId(),
                a.getTransferredFromId(), feedbackSubmitted, a.getSubmittedAt(), timeline);
    }

    private String[] statusText(Application a, LocalDateTime announce, TableInfo table, boolean feedbackSubmitted) {
        if (a.getAppStatus() == ApplicationStatus.CANCELED) {
            return new String[]{"신청 취소", a.getStatusReason() == null ? "신청이 취소되었어요." : a.getStatusReason()};
        }
        if (a.getPaymentStatus() == PaymentStatus.PENDING || a.getPaymentStatus() == PaymentStatus.FAILED) {
            return new String[]{a.getPaymentStatus() == PaymentStatus.FAILED ? "결제 실패" : "결제 대기",
                    "결제를 완료해야 " + fmt(announce) + " 자동 매칭 대상에 포함돼요."};
        }
        return switch (a.getMatchStatus()) {
            case WAITING -> new String[]{"매칭 대기 중", fmt(announce) + "에 자동 매칭 결과를 알려드려요."
                    + (a.getReallocationCount() > 0 && a.getStatusReason() != null ? " " + a.getStatusReason() : "")};
            case MATCHING -> new String[]{"매칭 중", "어울리는 분들로 테이블을 구성하고 있어요."};
            case REALLOCATING -> new String[]{"재배치 중", "조건에 맞는 다른 테이블을 찾고 있어요."};
            case CONFIRMED -> confirmedText(a, table, feedbackSubmitted);
            case ALTERNATIVE_OFFERED -> new String[]{"다른 일정을 골라주세요",
                    a.getStatusReason() + " 추천 일정으로 이동하거나, 이용권 보관·환불을 선택할 수 있어요."};
            case TRANSFERRED -> new String[]{"일정 이동 완료", "새 회차 신청으로 이동했어요. 기존 결제는 그대로 유지돼요."};
            case NO_MATCH -> new String[]{"매칭 종료", noMatchText(a)};
            case EXCEPTION -> new String[]{"확인 중", "운영팀이 신청 내용을 확인하고 있어요. 곧 안내드릴게요."};
        };
    }

    private String[] confirmedText(Application a, TableInfo table, boolean feedbackSubmitted) {
        AttendanceStatus att = a.getAttendanceStatus();
        if (att == AttendanceStatus.ATTENDED) {
            return new String[]{"참석 완료", feedbackSubmitted ? "피드백 감사해요! 다음 매칭에 반영할게요." : "오늘 식탁은 어떠셨나요? 피드백을 남겨주세요."};
        }
        if (att == AttendanceStatus.NO_SHOW) {
            return new String[]{"노쇼", "체크인 기록이 없어 노쇼로 처리되었어요. 이후 참여가 제한될 수 있어요."};
        }
        String venue = table == null || table.venueName() == null ? "식당 배정 중" : table.venueName();
        return new String[]{"테이블 확정", (table == null ? "" : table.slotLabel() + " · ") + venue + ". 단체방에서 안내를 확인해 주세요."};
    }

    private static String noMatchText(Application a) {
        return switch (a.getPaymentStatus()) {
            case REFUNDED -> "원결제수단으로 환불이 완료되었어요.";
            case REFUND_REQUESTED, REFUND_PROCESSING -> "환불을 처리하고 있어요.";
            case REFUND_FAILED -> "환불 처리 중 문제가 생겨 운영팀이 확인하고 있어요.";
            case REFUND_AVAILABLE -> (a.getStatusReason() == null ? "" : a.getStatusReason() + " ") + "이용권 보관 또는 환불을 선택해 주세요.";
            default -> a.getStatusReason() == null ? "이용권을 보관했어요." : a.getStatusReason();
        };
    }

    private List<String> actions(Application a, LocalDateTime now, EventSlot assigned, TableInfo table,
                                 boolean feedbackSubmitted, ResolutionInfo resolution) {
        List<String> actions = new ArrayList<>();
        if (a.getAppStatus() == ApplicationStatus.CANCELED) {
            actions.add("REAPPLY");
            return actions;
        }
        if (a.getPaymentStatus() == PaymentStatus.PENDING || a.getPaymentStatus() == PaymentStatus.FAILED) {
            actions.add("PAY");
        }
        switch (a.getMatchStatus()) {
            case WAITING -> actions.add("CANCEL");
            case CONFIRMED -> {
                if (table != null && table.roomId() != null) actions.add("VIEW_ROOM");
                if (table != null && table.canCheckIn()) actions.add("CHECK_IN");
                if (assigned != null && now.isBefore(assigned.startAt()) && !now.toLocalDate().equals(assigned.getDate())
                        && a.getAttendanceStatus() == AttendanceStatus.SCHEDULED) actions.add("CANCEL");
                if (a.getAttendanceStatus() == AttendanceStatus.ATTENDED && !feedbackSubmitted) actions.add("FEEDBACK");
                if (a.getAttendanceStatus() == AttendanceStatus.ATTENDED) actions.add("REAPPLY");
            }
            case ALTERNATIVE_OFFERED -> actions.add("CHOOSE_RESOLUTION");
            case NO_MATCH -> {
                if (resolution != null && "PENDING".equals(resolution.status())) actions.add("CHOOSE_RESOLUTION");
                else actions.add("REAPPLY");
            }
            case TRANSFERRED -> actions.add("VIEW_NEW_APPLICATION");
            default -> {
            }
        }
        return actions;
    }

    private static SlotItem slotItem(EventSlot s, boolean assigned) {
        return new SlotItem(s.getId(), s.label(), s.getRegion().name(), s.startAt(), s.getMatchAt(), s.getStatus().name(), assigned);
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "매칭 예정 시각" : FMT.format(t);
    }
}
