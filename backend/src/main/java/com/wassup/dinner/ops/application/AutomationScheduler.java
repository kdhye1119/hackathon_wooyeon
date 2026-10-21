package com.wassup.dinner.ops.application;

import com.wassup.dinner.application.application.ResolutionService;
import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.feedback.application.AttendanceService;
import com.wassup.dinner.matching.application.MatchingService;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 운영자 개입 없이 돌아가는 자동화 루프.
 * 1) 마감된 회차 자동 매칭·재배치·확정 → 2) 단체방 리마인드·대화 콘텐츠 게시
 * → 3) 종료 행사 참석/노쇼 정산·피드백 요청 → 매칭 실패 미선택 건 자동 보관 → 4) 알림 발송·재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationScheduler {

    private final MatchingService matchingService;
    private final ChatService chatService;
    private final AttendanceService attendanceService;
    private final NotificationService notificationService;
    private final ResolutionService resolutionService;
    private final com.wassup.dinner.matching.application.TableService tableService;

    @Value("${app.automation.enabled:true}")
    private boolean enabled;

    public record TickResult(Long matchRunId, int roomPostsSent, int attendanceSettled, int notificationsSent) {
    }

    @Scheduled(fixedDelayString = "${app.automation.tick-ms}", initialDelay = 5000)
    public void scheduledTick() {
        if (!enabled) {
            return;
        }
        tick(MatchRun.Trigger.SCHEDULER);
    }

    public synchronized TickResult tick(MatchRun.Trigger trigger) {
        Long runId = safe("matching", () -> matchingService.runDue(trigger).map(MatchRun::getId).orElse(null));
        Integer posts = safe("room-posts", chatService::dispatchDuePosts);
        Integer settled = safe("attendance", attendanceService::settleFinishedEvents);
        safe("auto-keep", resolutionService::autoKeepDue);
        safe("seat-offers", tableService::expireSeatOffers);
        Integer sent = safe("notifications", notificationService::dispatchDue);
        return new TickResult(runId, nz(posts), nz(settled), nz(sent));
    }

    private <T> T safe(String step, Supplier<T> task) {
        try {
            return task.get();
        } catch (RuntimeException e) {
            log.error("자동화 단계 실패: {}", step, e);
            return null;
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
