package com.wassup.dinner.application.application;

import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.payment.application.PaymentService;
import com.wassup.dinner.payment.application.RefundService;
import com.wassup.dinner.payment.domain.Ticket;
import com.wassup.dinner.payment.domain.TicketRepository;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.slot.domain.SlotStatus;
import com.wassup.dinner.user.domain.Options.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 매칭 실패 해결 (STA-04~06). 시스템은 선택지만 제시하고, 이동·보관·환불은 참가자가 고른다.
 */
@Service
@RequiredArgsConstructor
public class ResolutionService {

    private static final int MAX_ALTERNATIVES = 5;

    private final MatchResolutionRepository resolutionRepository;
    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository slotRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationStates states;
    private final RefundService refundService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final AppClock clock;

    /** 대체 일정 추천 + 선택 대기 상태로 전환 (멱등) */
    @Transactional
    public MatchResolution offer(Application app, String reason) {
        Optional<MatchResolution> existing = resolutionRepository.findByApplicationId(app.getId());
        if (existing.isPresent() && existing.get().getStatus() == MatchResolution.Status.PENDING) {
            return existing.get();
        }
        List<Long> alternatives = recommendAlternatives(app);
        MatchResolution r;
        if (existing.isPresent()) {
            // 충원 등으로 해결됐던 신청이 다시 매칭 실패한 경우: 기존 해결 건을 다시 연다
            r = existing.get();
            r.setAlternativeSlotIds(new java.util.ArrayList<>(alternatives));
            r.setReason(reason);
            r.setChoice(null);
            r.setChosenSlotId(null);
            r.setStatus(MatchResolution.Status.PENDING);
            r.setOfferedAt(clock.now());
            r.setResolvedAt(null);
        } else {
            r = resolutionRepository.save(new MatchResolution(app.getId(), app.getUserId(), alternatives, reason, clock.now()));
        }
        app.setFailedAt(clock.now());
        // FAQ Q5: 멤버십은 매칭 실패 1회마다 이용 기한을 연장하고 환불 대상이 아니다
        Ticket membership = membershipTicket(app);
        String extended = "";
        if (membership != null) {
            membership.extend(com.wassup.dinner.payment.domain.TicketProduct.MEMBERSHIP_EXTEND_DAYS);
            extended = " 멤버십 기간이 " + com.wassup.dinner.payment.domain.TicketProduct.MEMBERSHIP_EXTEND_DAYS + "일 연장됐어요.";
        }
        states.match(app, alternatives.isEmpty() ? MatchStatus.NO_MATCH : MatchStatus.ALTERNATIVE_OFFERED,
                reason + (alternatives.isEmpty() ? " 현재 이동 가능한 다른 일정이 없어요." : "") + extended, "SYSTEM");
        if (app.isPaid() && membership == null) {
            states.payment(app, PaymentStatus.REFUND_AVAILABLE, "매칭 불가로 환불 선택 가능", "SYSTEM");
        }
        notificationService.notify(app.getUserId(), NotificationType.MATCH_FAILED,
                NotificationService.vars("reason", reason), "app:" + app.getId(), app.getId(), null);
        return r;
    }

    /** 신청하지 않은 모집 중 회차 중에서 사용자가 고른 지역 우선, 가까운 날짜 순 */
    public List<Long> recommendAlternatives(Application app) {
        LocalDateTime now = clock.now();
        Set<Long> chosen = Set.copyOf(app.getSlotIds());
        Set<Region> regions = slotRepository.findAllById(app.getSlotIds()).stream().map(EventSlot::getRegion).collect(Collectors.toSet());
        Set<Long> alreadyActive = applicationRepository.findByUserIdOrderByIdDesc(app.getUserId()).stream()
                .filter(a -> !a.getId().equals(app.getId()) && a.isActive())
                .flatMap(a -> a.getSlotIds().stream()).collect(Collectors.toSet());
        return slotRepository.findByStatusOrderByDateAscStartTimeAsc(SlotStatus.RECRUITING).stream()
                .filter(s -> s.isOpen(now) && !chosen.contains(s.getId()) && !alreadyActive.contains(s.getId()))
                .sorted(Comparator.comparingInt((EventSlot s) -> regions.contains(s.getRegion()) ? 0 : 1)
                        .thenComparing(EventSlot::getDate))
                .limit(MAX_ALTERNATIVES).map(EventSlot::getId).toList();
    }

    /**
     * 참가자 선택 처리.
     * TRANSFER: 기존 결제·이용권을 그대로 새 회차 신청으로 옮긴다 (재결제 없음)
     * KEEP_TICKET: 이용권 1회 복구
     * REFUND: 원결제수단 자동 환불
     */
    @Transactional
    public MatchResolution choose(Long userId, Long applicationId, MatchResolution.Choice choice, Long slotId, String actor) {
        Application app = applicationRepository.findById(applicationId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("신청"));
        MatchResolution r = resolutionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE, "매칭 실패 상태의 신청만 선택할 수 있습니다."));
        if (r.getStatus() == MatchResolution.Status.COMPLETED) {
            return r; // 중복 클릭 → 같은 결과
        }
        if (!app.isPaid() && app.getPaymentStatus() != PaymentStatus.REFUND_AVAILABLE) {
            throw new ApiException(ErrorCode.INVALID_STATE, "결제 완료된 신청만 처리할 수 있습니다.");
        }
        switch (choice) {
            case TRANSFER -> transfer(app, r, slotId, actor);
            case KEEP_TICKET -> keepTicket(app, actor);
            case REFUND -> {
                if (membershipTicket(app) != null) {
                    throw new ApiException(ErrorCode.INVALID_STATE, "멤버십은 매칭 실패 시 환불 대신 이용 기한이 연장돼요.");
                }
                states.match(app, MatchStatus.NO_MATCH, "참가자가 환불을 선택했어요.", actor);
                refundService.requestAndProcess(app, "매칭 불가 - 참가자 환불 선택", actor);
            }
        }
        r.setChoice(choice);
        r.setStatus(MatchResolution.Status.COMPLETED);
        r.setResolvedAt(clock.now());
        return r;
    }

    /**
     * 선택 기한(실패한 회차 당일 12:00)까지 고르지 않은 매칭 실패 건은 자동으로 이용권을 보관한다.
     * 결제 전이거나 이용권이 없는 신청은 건너뛴다. 처리 건수를 돌려준다.
     */
    @Transactional
    public int autoKeepDue() {
        LocalDateTime now = clock.now();
        int done = 0;
        for (MatchResolution r : resolutionRepository.findByStatus(MatchResolution.Status.PENDING)) {
            Application app = applicationRepository.findById(r.getApplicationId()).orElse(null);
            if (app == null || app.getTicketId() == null) {
                continue;
            }
            LocalDateTime due = autoKeepAt(app);
            if (due != null && !now.isBefore(due)) {
                choose(app.getUserId(), app.getId(), MatchResolution.Choice.KEEP_TICKET, null, "SYSTEM");
                done++;
            }
        }
        return done;
    }

    /** 자동 보관 시각: 신청한 회차 중 가장 이른 날 12:00 */
    public LocalDateTime autoKeepAt(Application app) {
        return slotRepository.findAllById(app.getSlotIds()).stream().map(EventSlot::getDate).min(Comparator.naturalOrder())
                .map(d -> d.atTime(12, 0)).orElse(null);
    }

    /** 취소석 자동 충원으로 해결된 경우 */
    @Transactional
    public void closeAsRefilled(Long applicationId, Long slotId) {
        resolutionRepository.findByApplicationId(applicationId).ifPresent(r -> {
            r.setChoice(MatchResolution.Choice.TRANSFER);
            r.setChosenSlotId(slotId);
            r.setStatus(MatchResolution.Status.COMPLETED);
            r.setResolvedAt(clock.now());
        });
    }

    private void transfer(Application app, MatchResolution r, Long slotId, String actor) {
        if (slotId == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이동할 회차를 선택해 주세요.");
        }
        EventSlot slot = slotRepository.findById(slotId).orElseThrow(() -> ApiException.notFound("회차"));
        if (!slot.isOpen(clock.now())) {
            throw new ApiException(ErrorCode.SLOT_CLOSED);
        }
        Application moved = new Application(app.getUserId(), List.of(slotId), app.getOtherRegion(), clock.now());
        moved.setProduct(app.getProduct());
        moved.setTicketId(app.getTicketId());
        moved.setPaymentId(app.getPaymentId());
        moved.setPaidAt(app.getPaidAt());
        moved.setPaymentStatus(PaymentStatus.PAID);
        moved.setTransferredFromId(app.getId());
        moved.setStatusReason("매칭 실패 후 대체 일정으로 이동");
        applicationRepository.save(moved);

        app.setTransferredToId(moved.getId());
        states.payment(app, PaymentStatus.PAID, "결제를 새 신청#" + moved.getId() + "으로 이관", actor);
        states.match(app, MatchStatus.TRANSFERRED, slot.label() + " 회차로 일정 이동", actor);
        r.setChosenSlotId(slotId);
        r.setNewApplicationId(moved.getId());
        notificationService.notify(app.getUserId(), NotificationType.TRANSFERRED,
                NotificationService.vars("slot", slot.label(), "matchAt", PaymentService.at(slot.getMatchAt())),
                "app:" + app.getId(), moved.getId(), null);
    }

    private void keepTicket(Application app, String actor) {
        Ticket ticket = app.getTicketId() == null ? null : ticketRepository.findById(app.getTicketId()).orElse(null);
        if (ticket == null) {
            throw new ApiException(ErrorCode.TICKET_UNAVAILABLE, "보관할 이용권이 없습니다.");
        }
        ticket.restore();
        if (ticket.isMembership()) {
            states.payment(app, PaymentStatus.PAID, "멤버십 유지 (기간 연장 반영)", actor);
            states.match(app, MatchStatus.NO_MATCH, "멤버십 기간이 연장됐어요. 다음 일정에 다시 신청할 수 있어요.", actor);
        } else {
            states.payment(app, PaymentStatus.PAID, "이용권 1회 복구 (보관)", actor);
            states.match(app, MatchStatus.NO_MATCH, "이용권을 보관했어요. 다음 신청에 사용할 수 있어요.", actor);
        }
        notificationService.notify(app.getUserId(), NotificationType.TICKET_KEPT,
                NotificationService.vars("expiresAt", PaymentService.date(ticket.getExpiresAt())),
                "app:" + app.getId(), app.getId(), null);
    }

    private Ticket membershipTicket(Application app) {
        if (app.getTicketId() == null) {
            return null;
        }
        return ticketRepository.findById(app.getTicketId()).filter(Ticket::isMembership).orElse(null);
    }
}
