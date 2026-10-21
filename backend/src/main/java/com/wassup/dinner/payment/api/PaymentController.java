package com.wassup.dinner.payment.api;

import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.payment.application.PaymentService;
import com.wassup.dinner.payment.application.PaymentService.PaymentDto;
import com.wassup.dinner.payment.application.PaymentService.TicketDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Payment", description = "모의 결제·이용권")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    public record ConfirmRequest(boolean simulateFailure) {
    }

    @Operation(summary = "결제 승인 (모의 PG). simulateFailure=true 면 카드 승인 거절을 시뮬레이션")
    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentDto confirm(LoginUser user, @PathVariable Long paymentId,
                              @RequestBody(required = false) ConfirmRequest request) {
        return paymentService.confirm(user.userId(), paymentId, request != null && request.simulateFailure());
    }

    @Operation(summary = "내 결제 내역")
    @GetMapping("/me/payments")
    public List<PaymentDto> payments(LoginUser user) {
        return paymentService.myPayments(user.userId());
    }

    @Operation(summary = "보유 이용권 (남은 횟수·유효기간)")
    @GetMapping("/me/tickets")
    public List<TicketDto> tickets(LoginUser user) {
        return paymentService.myTickets(user.userId());
    }
}
