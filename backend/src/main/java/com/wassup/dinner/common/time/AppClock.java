package com.wassup.dinner.common.time;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 서비스 전역 시계. 데모 시연을 위해 운영자가 시간을 앞으로 돌릴 수 있다(offset).
 * 도메인 로직은 LocalDateTime.now() 대신 이 클래스를 사용한다.
 */
@Component
public class AppClock {

    private volatile Duration offset = Duration.ZERO;

    public LocalDateTime now() {
        return LocalDateTime.now().plus(offset);
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public Duration getOffset() {
        return offset;
    }

    public void setOffset(Duration offset) {
        this.offset = offset;
    }

    public void travelTo(LocalDateTime target) {
        this.offset = Duration.between(LocalDateTime.now(), target);
    }
}
