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
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.payment.domain.Payment;
import com.wassup.dinner.payment.domain.PaymentRepository;
import com.wassup.dinner.payment.domain.Refund;
import com.wassup.dinner.payment.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원결제수단 자동 환불. 계좌 전달·운영자 승인 없이 처리하고, PG 실패 시에만 운영자 예외로 전환한다.
 * 신청 1건당 환불 1건 (Refund.applicationId 유니크) → 중복 환불 불가.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStates states;
    private final MockPgClient pg;
    private final NotificationService notificationService;
    private final ExceptionCaseService exceptionCaseService;
    private final AppClock clock;

    @Transactional
    public Refund requestAndProcess(Application app, String reason, String actor) {
        Refund existing = refundRepository.findByApplicationId(app.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        if (app.getPaymentId() == null) {
            throw new ApiException(ErrorCode.INVALID_STATE, "결제 내역이 없는 신청입니다.");
        }
        Payment payment = paymentRepository.findById(app.getPaymentId()).orElseThrow();
        int amount = payment.getProduct().unitPrice();
        Refund refund = refundRepository.save(new Refund(app.getId(), payment.getId(), app.getUserId(), amount, reason, clock.now()));
        states.payment(app, PaymentStatus.REFUND_REQUESTED, reason, actor);
        notificationService.notify(app.getUserId(), NotificationType.REFUND_REQUESTED,
                NotificationService.vars("amount", String.format("%,d", amount)), "app:" + app.getId(), app.getId(), null);
        process(refund, app, payment);
        return refund;
    }

    /** 운영자 재시도 (환불 실패 예외 처리) */
    @Transactional
    public Refund retry(Long refundId, String actor) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(() -> ApiException.notFound("환불"));
        if (refund.getStatus() != Refund.Status.FAILED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "실패한 환불만 재시도할 수 있습니다.");
        }
        Application app = applicationRepository.findById(refund.getApplicationId()).orElseThrow();
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        process(refund, app, payment);
        if (refund.getStatus() == Refund.Status.COMPLETED) {
            exceptionCaseService.autoResolve(ExceptionType.REFUND_FAILED, "refund:" + refund.getId(), "재시도 성공 (" + actor + ")");
        }
        return refund;
    }

    private void process(Refund refund, Application app, Payment payment) {
        refund.setStatus(Refund.Status.PROCESSING);
        refund.setAttempts(refund.getAttempts() + 1);
        states.payment(app, PaymentStatus.REFUND_PROCESSING, null, "SYSTEM");
        MockPgClient.PgResult result = pg.refund(payment.getPgTxId(), refund.getAmount());
        if (result.success()) {
            refund.setStatus(Refund.Status.COMPLETED);
            refund.setPgRefundId(result.txId());
            refund.setCompletedAt(clock.now());
            refund.setFailureReason(null);
            payment.addRefund(refund.getAmount());
            states.payment(app, PaymentStatus.REFUNDED, "원결제수단 자동 환불 완료", "SYSTEM");
            notificationService.notify(app.getUserId(), NotificationType.REFUND_COMPLETED,
                    NotificationService.vars("amount", String.format("%,d", refund.getAmount())),
                    "app:" + app.getId(), app.getId(), null);
        } else {
            refund.setStatus(Refund.Status.FAILED);
            refund.setFailureReason(result.message());
            states.payment(app, PaymentStatus.REFUND_FAILED, result.message(), "SYSTEM");
            exceptionCaseService.open(ExceptionType.REFUND_FAILED, "refund:" + refund.getId(),
                    "신청#" + app.getId() + " " + refund.getAmount() + "원 자동 환불 실패: " + result.message(),
                    ExceptionCaseService.Refs.application(app.getId(), app.getUserId()));
        }
    }
}
