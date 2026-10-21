package com.wassup.dinner.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledRoomPostRepository extends JpaRepository<ScheduledRoomPost, Long> {

    boolean existsByDedupKey(String dedupKey);

    List<ScheduledRoomPost> findByStatusAndScheduledAtLessThanEqualOrderByIdAsc(ScheduledRoomPost.Status status, LocalDateTime now);

    List<ScheduledRoomPost> findByTableId(Long tableId);

    long countByStatus(ScheduledRoomPost.Status status);

    List<ScheduledRoomPost> findByStatus(ScheduledRoomPost.Status status);
}
