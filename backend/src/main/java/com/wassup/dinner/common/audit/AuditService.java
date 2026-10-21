package com.wassup.dinner.common.audit;

import com.wassup.dinner.common.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String SYSTEM = "SYSTEM";

    private final StatusHistoryRepository repository;
    private final AppClock clock;

    public void record(String entityType, Long entityId, String field, Object from, Object to, String reason, String actor) {
        if (Objects.equals(from, to) && reason == null) {
            return;
        }
        repository.save(new StatusHistory(entityType, entityId, field,
                from == null ? null : from.toString(), to == null ? null : to.toString(),
                truncate(reason), actor == null ? SYSTEM : actor, clock.now()));
    }

    public List<StatusHistory> historyOf(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
