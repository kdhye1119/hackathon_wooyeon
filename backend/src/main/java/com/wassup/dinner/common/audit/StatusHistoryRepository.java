package com.wassup.dinner.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);
}
