package com.wassup.dinner.payment.application;

import com.wassup.dinner.ops.application.DemoSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 모의 PG. 실결제 연동(PAY-04) 시 이 클래스만 실제 PG 클라이언트로 교체한다. */
@Component
@RequiredArgsConstructor
public class MockPgClient {

    private final DemoSettings demoSettings;

    public record PgResult(boolean success, String txId, String message) {
    }

    public PgResult approve(int amount, boolean simulateFailure) {
        if (simulateFailure) {
            return new PgResult(false, null, "카드 승인 거절 (모의)");
        }
        return new PgResult(true, "PAY-" + UUID.randomUUID().toString().substring(0, 8), "승인 완료");
    }

    public PgResult refund(String paymentTxId, int amount) {
        if (demoSettings.consumeRefundFailure()) {
            return new PgResult(false, null, "PG 환불 API 응답 오류 (모의)");
        }
        return new PgResult(true, "RFD-" + UUID.randomUUID().toString().substring(0, 8), "환불 완료");
    }
}
