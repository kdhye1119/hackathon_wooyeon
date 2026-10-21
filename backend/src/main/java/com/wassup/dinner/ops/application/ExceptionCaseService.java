package com.wassup.dinner.ops.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.ops.domain.ExceptionCase;
import com.wassup.dinner.ops.domain.ExceptionCaseRepository;
import com.wassup.dinner.ops.domain.ExceptionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExceptionCaseService {

    private final ExceptionCaseRepository repository;
    private final AppClock clock;

    public record Refs(Long applicationId, Long tableId, Long slotId, Long userId) {
        public static Refs application(Long applicationId, Long userId) {
            return new Refs(applicationId, null, null, userId);
        }

        public static Refs table(Long tableId, Long slotId) {
            return new Refs(null, tableId, slotId, null);
        }
    }

    /** 같은 dedupKey 의 예외가 이미 있으면 그대로 반환한다 (멱등). */
    @Transactional
    public ExceptionCase open(ExceptionType type, String dedupKey, String reason, Refs refs) {
        String key = type.name() + ":" + dedupKey;
        return repository.findByDedupKey(key).orElseGet(() -> {
            ExceptionCase c = new ExceptionCase(type, key, reason, clock.now());
            c.setApplicationId(refs.applicationId());
            c.setTableId(refs.tableId());
            c.setSlotId(refs.slotId());
            c.setUserId(refs.userId());
            return repository.save(c);
        });
    }

    @Transactional
    public ExceptionCase close(Long id, ExceptionCase.Status status, String note, String actor) {
        ExceptionCase c = repository.findById(id).orElseThrow(() -> ApiException.notFound("예외"));
        c.setStatus(status);
        c.setAdminNote(note);
        c.setResolvedBy(actor);
        c.setResolvedAt(clock.now());
        return c;
    }

    /** 자동 처리로 해소된 예외(예: 식당 수동 배정 완료)를 닫는다. */
    @Transactional
    public void autoResolve(ExceptionType type, String dedupKey, String note) {
        repository.findByDedupKey(type.name() + ":" + dedupKey)
                .filter(c -> c.getStatus() == ExceptionCase.Status.OPEN)
                .ifPresent(c -> {
                    c.setStatus(ExceptionCase.Status.RESOLVED);
                    c.setAdminNote(note);
                    c.setResolvedBy("SYSTEM");
                    c.setResolvedAt(clock.now());
                });
    }
}
