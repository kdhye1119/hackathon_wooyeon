package com.wassup.dinner.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupKey(String dedupKey);

    List<Notification> findByStatusAndScheduledAtLessThanEqualOrderByIdAsc(Notification.Status status, LocalDateTime now);

    List<Notification> findByUserIdAndStatusOrderByIdDesc(Long userId, Notification.Status status);

    List<Notification> findByUserIdInOrderByIdDesc(java.util.Collection<Long> userIds);

    List<Notification> findTop200ByOrderByIdDesc();

    long countByStatus(Notification.Status status);
}
