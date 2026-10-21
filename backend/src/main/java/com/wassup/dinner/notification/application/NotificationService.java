package com.wassup.dinner.notification.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.notification.domain.*;
import com.wassup.dinner.ops.application.DemoSettings;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String USER_QUEUE = "/queue/notifications";

    private final NotificationRepository repository;
    private final MessageTemplateRepository templateRepository;
    private final AppClock clock;
    private final DemoSettings demoSettings;
    private final ExceptionCaseService exceptionCaseService;
    private final SimpMessagingTemplate messaging;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    public record NotificationDto(Long id, String type, String title, String body, Long applicationId, Long tableId,
                                  String status, int attempts, LocalDateTime scheduledAt, LocalDateTime sentAt,
                                  LocalDateTime readAt) {
        public static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType().name(), n.getTitle(), n.getBody(), n.getApplicationId(),
                    n.getTableId(), n.getStatus().name(), n.getAttempts(), n.getScheduledAt(), n.getSentAt(), n.getReadAt());
        }
    }

    /** null 허용 변수 맵 */
    public static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Transactional
    public void notify(Long userId, NotificationType type, Map<String, Object> vars, String dedupKey,
                       Long applicationId, Long tableId) {
        schedule(userId, type, vars, clock.now(), dedupKey, applicationId, tableId);
    }

    /** 예약 알림. 같은 dedupKey 가 이미 있으면 무시 (중복 발송 방지). */
    @Transactional
    public void schedule(Long userId, NotificationType type, Map<String, Object> vars, LocalDateTime at,
                         String dedupKey, Long applicationId, Long tableId) {
        String key = type.name() + ":" + dedupKey;
        if (repository.existsByDedupKey(key)) {
            return;
        }
        MessageTemplate t = templateRepository.findByCode(type.name()).orElse(null);
        String title = render(t == null ? type.getDefaultTitle() : t.getTitle(), vars);
        String body = render(t == null ? type.getDefaultBody() : t.getBody(), vars);
        Notification n = new Notification(userId, type, title, body, at, key, clock.now());
        n.setApplicationId(applicationId);
        n.setTableId(tableId);
        repository.save(n);
    }

    /** 발송 시각이 된 알림을 보낸다. 실패 시 재시도하고, 반복 실패만 운영자 예외로 전환한다. */
    @Transactional
    public int dispatchDue() {
        LocalDateTime now = clock.now();
        List<Notification> due = repository.findByStatusAndScheduledAtLessThanEqualOrderByIdAsc(Notification.Status.PENDING, now);
        int sent = 0;
        for (Notification n : due) {
            n.setAttempts(n.getAttempts() + 1);
            if (ThreadLocalRandom.current().nextDouble() < demoSettings.getNotificationFailureRate()) {
                n.setLastError("푸시 발송 실패 (모의)");
                if (n.getAttempts() >= maxRetries) {
                    n.setStatus(Notification.Status.FAILED);
                    exceptionCaseService.open(ExceptionType.NOTIFICATION_FAILED, "notification:" + n.getId(),
                            "알림 '" + n.getTitle() + "' 이(가) " + n.getAttempts() + "회 발송 실패했습니다.",
                            new ExceptionCaseService.Refs(n.getApplicationId(), n.getTableId(), null, n.getUserId()));
                } else {
                    n.setScheduledAt(now.plusSeconds(30L * n.getAttempts()));
                }
                continue;
            }
            n.setStatus(Notification.Status.SENT);
            n.setSentAt(now);
            n.setLastError(null);
            messaging.convertAndSendToUser(String.valueOf(n.getUserId()), USER_QUEUE, NotificationDto.of(n));
            sent++;
        }
        return sent;
    }

    /** 운영자 재발송 */
    @Transactional
    public NotificationDto retry(Long id) {
        Notification n = repository.findById(id).orElseThrow(() -> ApiException.notFound("알림"));
        n.setStatus(Notification.Status.PENDING);
        n.setAttempts(0);
        n.setScheduledAt(clock.now());
        exceptionCaseService.autoResolve(ExceptionType.NOTIFICATION_FAILED, "notification:" + id, "운영자 재발송");
        return NotificationDto.of(n);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> myNotifications(Long userId) {
        return repository.findByUserIdAndStatusOrderByIdDesc(userId, Notification.Status.SENT).stream()
                .map(NotificationDto::of).toList();
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repository.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("알림"));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.now());
        }
    }

    static String render(String template, Map<String, Object> vars) {
        String out = template;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "-" : String.valueOf(e.getValue()));
        }
        return out;
    }
}
