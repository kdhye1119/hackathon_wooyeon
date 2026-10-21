package com.wassup.dinner.ops.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 시연용 장애 시뮬레이션 스위치. 운영자 데모 API 로 런타임에 변경한다. */
@Getter
@Setter
@Component
public class DemoSettings {

    /** 알림·단체방 메시지 발송 실패 확률 (0.0 ~ 1.0) */
    private volatile double notificationFailureRate;
    /** true 면 다음 환불 1건을 PG 실패로 처리 */
    private volatile boolean failNextRefund;

    public DemoSettings(@Value("${app.notification.failure-rate:0.0}") double notificationFailureRate) {
        this.notificationFailureRate = notificationFailureRate;
    }

    public synchronized boolean consumeRefundFailure() {
        boolean fail = failNextRefund;
        failNextRefund = false;
        return fail;
    }
}
