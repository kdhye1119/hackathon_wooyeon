package com.wassup.dinner.admin.api;

import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.ops.application.AutomationScheduler;
import com.wassup.dinner.ops.application.AutomationScheduler.TickResult;
import com.wassup.dinner.ops.application.DemoService;
import com.wassup.dinner.ops.application.DemoService.DemoUser;
import com.wassup.dinner.ops.application.DemoService.ScenarioResponse;
import com.wassup.dinner.ops.application.DemoService.SeedRequest;
import com.wassup.dinner.ops.application.DemoSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Admin - Demo", description = "해커톤 시연 도구: 시간 이동·자동화 즉시 실행·테스트 참가자·장애 시뮬레이션")
@RestController
@RequestMapping("/api/admin/demo")
@RequiredArgsConstructor
public class AdminDemoController {

    private final AppClock clock;
    private final AutomationScheduler scheduler;
    private final DemoService demoService;
    private final DemoSettings demoSettings;

    public record ClockRequest(Long plusMinutes, Long plusHours, Long plusDays, LocalDateTime at) {
    }

    public record ClockResponse(LocalDateTime now, long offsetMinutes) {
    }

    public record SettingsRequest(Double notificationFailureRate, Boolean failNextRefund) {
    }

    @Operation(summary = "현재 서비스 시각")
    @GetMapping("/clock")
    public ClockResponse clock() {
        return new ClockResponse(clock.now(), clock.getOffset().toMinutes());
    }

    @Operation(summary = "서비스 시각 이동 (plusMinutes/plusHours/plusDays 누적 또는 at 지정)")
    @PostMapping("/clock")
    public ClockResponse travel(@RequestBody ClockRequest r) {
        if (r.at() != null) {
            clock.travelTo(r.at());
        } else {
            Duration d = Duration.ofMinutes(nz(r.plusMinutes())).plusHours(nz(r.plusHours())).plusDays(nz(r.plusDays()));
            clock.setOffset(clock.getOffset().plus(d));
        }
        return clock();
    }

    @Operation(summary = "서비스 시각 초기화")
    @PostMapping("/clock/reset")
    public ClockResponse reset() {
        clock.setOffset(Duration.ZERO);
        return clock();
    }

    @Operation(summary = "자동화 1회 즉시 실행: 마감 회차 매칭 → 단체방 예약 게시 → 참석 정산 → 알림 발송")
    @PostMapping("/tick")
    public TickResult tick() {
        return scheduler.tick(MatchRun.Trigger.ADMIN);
    }

    @Operation(summary = "테스트 참가자 생성 + 신청 + (선택) 결제. 응답의 token 으로 참가자 API 호출 가능")
    @PostMapping("/participants")
    public List<DemoUser> seed(@RequestBody SeedRequest request) {
        return demoService.seed(request);
    }

    @Operation(summary = "시연 시나리오 일괄 생성 (분리·재배치·매칭 실패가 모두 나오도록 구성)")
    @PostMapping("/scenario")
    public ScenarioResponse scenario(@RequestParam(defaultValue = "42") long seed) {
        return demoService.scenario(seed);
    }

    public record UserScenarioRequest(Long userId, DemoService.UserScenario type) {
    }

    @Operation(summary = "참가자 본인 기준 시나리오 (매칭 성공·인원 부족 실패·나이 차이 실패·멤버십 실패·자동 재배치·결제 대기)")
    @PostMapping("/user-scenario")
    public DemoService.UserScenarioResponse userScenario(@RequestBody UserScenarioRequest r) {
        return demoService.userScenario(r.userId(), r.type());
    }

    @Operation(summary = "장애 시뮬레이션: 알림 발송 실패율, 다음 환불 PG 실패")
    @PostMapping("/settings")
    public DemoSettings settings(@RequestBody SettingsRequest r) {
        if (r.notificationFailureRate() != null) demoSettings.setNotificationFailureRate(r.notificationFailureRate());
        if (r.failNextRefund() != null) demoSettings.setFailNextRefund(r.failNextRefund());
        return demoSettings;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
