package com.wassup.dinner.payment.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.application.domain.ApplicationStates;
import com.wassup.dinner.application.domain.PaymentStatus;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.payment.domain.*;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository slotRepository;
    private final ApplicationStates states;
    private final MockPgClient pg;
    private final NotificationService notificationService;
    private final AppClock clock;

    public record PaymentDto(Long id, String product, String productLabel, int amount, int refundedAmount,
                             String status, String method, String pgTxId, String failureReason,
                             LocalDateTime createdAt, LocalDateTime paidAt) {
        public static PaymentDto of(Payment p) {
            return new PaymentDto(p.getId(), p.getProduct().name(), p.getProduct().getLabel(), p.getAmount(),
                    p.getRefundedAmount(), p.getStatus().name(), p.getMethod(), p.getPgTxId(), p.getFailureReason(),
                    p.getCreatedAt(), p.getPaidAt());
        }
    }

    public record TicketDto(Long id, String product, String productLabel, int totalCount, int remainingCount,
                            LocalDateTime expiresAt, boolean usable) {
    }

    @Transactional
    public Payment createPending(Long userId, TicketProduct product) {
        return paymentRepository.save(new Payment(userId, product, clock.now()));
    }

    /**
     * 모의 결제 승인. 성공 시 이용권 발급 → 신청에 1회 사용 → 신청 결제 상태 PAID.
     * 이미 결제된 건은 그대로 반환해 중복 승인을 막는다.
     */
    @Transactional
    public PaymentDto confirm(Long userId, Long paymentId, boolean simulateFailure) {
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("결제"));
        if (payment.getStatus() == Payment.Status.PAID) {
            return PaymentDto.of(payment);
        }
        if (payment.getStatus() != Payment.Status.PENDING && payment.getStatus() != Payment.Status.FAILED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "결제할 수 없는 상태입니다.");
        }
        List<Application> apps = applicationRepository.findByPaymentId(paymentId);
        for (Application a : apps) {
            boolean expired = a.getSlotIds().stream().map(slotRepository::findById)
                    .flatMap(java.util.Optional::stream).noneMatch(s -> s.isOpen(clock.now()));
            if (expired) {
                throw new ApiException(ErrorCode.SLOT_CLOSED, "결제 기한(신청 마감)이 지났습니다.");
            }
        }
        MockPgClient.PgResult result = pg.approve(payment.getAmount(), simulateFailure);
        String actor = "USER:" + userId;
        if (!result.success()) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailureReason(result.message());
            apps.forEach(a -> states.payment(a, PaymentStatus.FAILED, result.message(), actor));
            return PaymentDto.of(payment);
        }
        payment.setStatus(Payment.Status.PAID);
        payment.setPgTxId(result.txId());
        payment.setPaidAt(clock.now());
        payment.setFailureReason(null);
        Ticket ticket = ticketRepository.save(new Ticket(userId, payment.getProduct(), payment.getId(), clock.now()));
        for (Application a : apps) {
            ticket.use(clock.now());
            a.setTicketId(ticket.getId());
            a.setPaidAt(clock.now());
            states.payment(a, PaymentStatus.PAID, "모의 결제 승인 " + result.txId(), actor);
            notificationService.notify(userId, NotificationType.PAYMENT_COMPLETED,
                    NotificationService.vars("product", payment.getProduct().getLabel(), "matchAt", matchAt(a)),
                    "app:" + a.getId(), a.getId(), null);
        }
        return PaymentDto.of(payment);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> myTickets(Long userId) {
        LocalDateTime now = clock.now();
        return ticketRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(t -> new TicketDto(t.getId(), t.getProduct().name(), t.getProduct().getLabel(), t.getTotalCount(),
                        t.getRemainingCount(), t.getExpiresAt(), t.isUsable(now)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> myPayments(Long userId) {
        return paymentRepository.findByUserIdOrderByIdDesc(userId).stream().map(PaymentDto::of).toList();
    }

    /** 신청의 가장 이른 자동 매칭 예정 시각 */
    public String matchAt(Application a) {
        return a.getSlotIds().stream().map(slotRepository::findById).flatMap(java.util.Optional::stream)
                .map(EventSlot::getMatchAt).min(Comparator.naturalOrder()).map(FMT::format).orElse("-");
    }
}
