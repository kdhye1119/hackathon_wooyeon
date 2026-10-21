package com.wassup.dinner.application.application;

import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.application.TableService;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.payment.application.PaymentService;
import com.wassup.dinner.payment.application.RefundService;
import com.wassup.dinner.payment.domain.*;
import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options.Region;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationStates states;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final ResolutionService resolutionService;
    private final TableService tableService;
    private final SlotService slotService;
    private final NotificationService notificationService;
    private final ExceptionCaseService exceptionCaseService;
    private final ApplicationViewAssembler assembler;
    private final AppClock clock;

    public record SubmitRequest(List<LocalDate> dates, List<Region> regions, String otherRegion,
                                TicketProduct product, Long ticketId) {
    }

    public record SubmitResponse(ApplicationView application, Long paymentId, int amount, boolean paymentRequired) {
    }

    /**
     * 신청 제출. 날짜 × 지역 조합으로 모집 중인 회차를 찾아 희망 회차 목록(우선순위 순)을 만든다.
     * 보유 이용권(ticketId)이 있으면 바로 결제 완료, 없으면 상품(product) 결제 대기 건을 만든다.
     */
    @Transactional
    public SubmitResponse submit(Long userId, SubmitRequest r) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
        if (!user.canApply(clock.today())) {
            throw new ApiException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        if (!user.isProfileComplete()) {
            throw new ApiException(ErrorCode.PROFILE_INCOMPLETE);
        }
        if (!user.hasConsented()) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED);
        }
        if (r.dates() == null || r.dates().isEmpty() || r.regions() == null || r.regions().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "참가 가능한 날짜와 희망 지역을 하나 이상 선택해 주세요.");
        }
        if (r.ticketId() == null && r.product() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "참가권을 선택해 주세요.");
        }
        LocalDateTime now = clock.now();
        Map<Long, Long> counts = slotService.activeCounts();
        Map<String, EventSlot> byKey = slotRepository.findByDateInAndRegionIn(r.dates(), r.regions()).stream()
                .filter(s -> s.isOpen(now) && counts.getOrDefault(s.getId(), 0L) < s.getCapacity())
                .collect(Collectors.toMap(s -> s.getDate() + "|" + s.getRegion(), s -> s, (a, b) -> a));
        List<Long> slotIds = new ArrayList<>();
        for (LocalDate d : r.dates()) {
            for (Region region : r.regions()) {
                EventSlot s = byKey.get(d + "|" + region);
                if (s != null) {
                    slotIds.add(s.getId());
                }
            }
        }
        if (slotIds.isEmpty()) {
            throw new ApiException(ErrorCode.SLOT_CLOSED, "선택한 날짜·지역에 신청 가능한 회차가 없습니다.");
        }
        Set<Long> active = applicationRepository.findByUserIdOrderByIdDesc(userId).stream()
                .filter(Application::isActive).flatMap(a -> a.getSlotIds().stream()).collect(Collectors.toSet());
        if (slotIds.stream().anyMatch(active::contains)) {
            throw new ApiException(ErrorCode.DUPLICATE_APPLICATION);
        }

        Application app = applicationRepository.save(new Application(userId, slotIds, r.otherRegion(), now));
        String actor = "USER:" + userId;
        Payment payment;
        if (r.ticketId() != null) {
            Ticket ticket = ticketRepository.findById(r.ticketId())
                    .filter(t -> t.getUserId().equals(userId) && t.isUsable(now))
                    .orElseThrow(() -> new ApiException(ErrorCode.TICKET_UNAVAILABLE));
            ticket.use(now);
            payment = paymentRepository.findById(ticket.getPaymentId()).orElseThrow();
            app.setTicketId(ticket.getId());
            app.setProduct(ticket.getProduct());
            app.setPaymentId(payment.getId());
            app.setPaidAt(now);
            states.payment(app, PaymentStatus.PAID, ticket.isMembership() ? "멤버십 사용" : "보유 이용권 사용 (남은 " + ticket.getRemainingCount() + "회)", actor);
        } else {
            payment = paymentService.createPending(userId, r.product());
            app.setProduct(r.product());
            app.setPaymentId(payment.getId());
            notificationService.notify(userId, NotificationType.PAYMENT_PENDING,
                    NotificationService.vars("product", r.product().getLabel(), "amount", String.format("%,d", r.product().getPrice()),
                            "deadline", PaymentService.FMT.format(earliest(slotIds).getApplyDeadline())),
                    "app:" + app.getId(), app.getId(), null);
        }
        states.app(app, ApplicationStatus.SUBMITTED, "신청 제출", actor);
        notificationService.notify(userId, NotificationType.APPLICATION_SUBMITTED,
                NotificationService.vars("slots", slotRepository.findAllById(slotIds).stream()
                        .map(EventSlot::label).collect(Collectors.joining(", "))),
                "app:" + app.getId(), app.getId(), null);
        boolean paymentRequired = !app.isPaid();
        return new SubmitResponse(assembler.toView(app, false), payment.getId(), payment.getAmount(), paymentRequired);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> mine(Long userId) {
        return applicationRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(a -> assembler.toView(a, false)).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationView detail(Long userId, Long applicationId) {
        return assembler.toView(owned(userId, applicationId), true);
    }

    /**
     * 참가자 취소.
     * - 매칭 전: 1회권은 원결제수단 환불, 3회권은 이용권 1회 복구 (진행 2일 전 이후엔 환불 불가)
     * - 확정 후: 진행 2일 전까지 사전 취소(환불/복구), 이후 제한 시점 취소(환불 불가·이용 제한 검토), 당일 취소 불가
     */
    @Transactional
    public ApplicationView cancel(Long userId, Long applicationId) {
        Application a = owned(userId, applicationId);
        String actor = "USER:" + userId;
        LocalDateTime now = clock.now();
        if (a.getAppStatus() != ApplicationStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "이미 취소된 신청입니다.");
        }
        switch (a.getMatchStatus()) {
            case WAITING -> {
                EventSlot first = earliest(a.getSlotIds());
                boolean refundable = now.isBefore(first.refundDeadline());
                states.app(a, ApplicationStatus.CANCELED, "참가자 취소 (매칭 전)", actor);
                a.setCanceledAt(now);
                settleCanceled(a, refundable, actor);
            }
            case CONFIRMED -> {
                EventSlot slot = slotRepository.findById(a.getAssignedSlotId()).orElseThrow();
                if (!now.isBefore(slot.startAt()) || now.toLocalDate().equals(slot.getDate())) {
                    throw new ApiException(ErrorCode.CANCEL_NOT_ALLOWED);
                }
                boolean early = now.isBefore(slot.refundDeadline());
                states.attendance(a, early ? AttendanceStatus.CANCELED_EARLY : AttendanceStatus.CANCELED_LATE,
                        early ? "사전 취소" : "환불 제한 시점 이후 취소", actor);
                states.app(a, ApplicationStatus.CANCELED, "참가자 취소 (확정 후)", actor);
                a.setCanceledAt(now);
                settleCanceled(a, early, actor);
                if (!early) {
                    exceptionCaseService.open(ExceptionType.LATE_CANCEL, "app:" + a.getId(),
                            slot.label() + " 확정 후 제한 시점 이후 취소 - 이용 제한 검토 대상",
                            ExceptionCaseService.Refs.application(a.getId(), a.getUserId()));
                }
                tableService.handleSeatReleased(a.getTableId());
            }
            case ALTERNATIVE_OFFERED, NO_MATCH -> throw new ApiException(ErrorCode.INVALID_STATE,
                    "매칭 실패 신청은 일정 이동·이용권 보관·환불 중에서 선택해 주세요.");
            default -> throw new ApiException(ErrorCode.INVALID_STATE, "매칭이 진행 중이라 지금은 취소할 수 없어요.");
        }
        return assembler.toView(a, true);
    }

    private void settleCanceled(Application a, boolean refundable, String actor) {
        String message;
        if (!a.isPaid()) {
            paymentRepository.findById(a.getPaymentId()).filter(p -> p.getStatus() != Payment.Status.PAID)
                    .ifPresent(p -> {
                        p.setStatus(Payment.Status.FAILED);
                        p.setFailureReason("신청 취소");
                    });
            message = "신청이 취소되었어요.";
        } else if (!refundable) {
            message = "진행 2일 전 이후 취소로 환불되지 않아요.";
        } else if (a.getProduct() == TicketProduct.SINGLE) {
            refundService.requestAndProcess(a, "참가자 취소", actor);
            message = "신청이 취소되어 원결제수단으로 환불돼요.";
        } else if (a.getProduct() != null && a.getProduct().isMembership()) {
            message = "신청이 취소되었어요. 멤버십 기간 안에 다시 신청할 수 있어요.";
        } else {
            ticketRepository.findById(a.getTicketId()).ifPresent(Ticket::restore);
            message = "신청이 취소되어 이용권 1회가 복구되었어요.";
        }
        a.setStatusReason(message);
        notificationService.notify(a.getUserId(), NotificationType.CANCELED, NotificationService.vars("reason", message),
                "app:" + a.getId(), a.getId(), null);
    }

    @Transactional
    public ApplicationView resolve(Long userId, Long applicationId, MatchResolution.Choice choice, Long slotId) {
        resolutionService.choose(userId, applicationId, choice, slotId, "USER:" + userId);
        return assembler.toView(owned(userId, applicationId), true);
    }

    private Application owned(Long userId, Long applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("신청"));
    }

    private EventSlot earliest(List<Long> slotIds) {
        return slotRepository.findAllById(slotIds).stream().min(Comparator.comparing(EventSlot::startAt))
                .orElseThrow(() -> ApiException.notFound("회차"));
    }
}
