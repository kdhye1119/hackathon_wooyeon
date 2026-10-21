package com.wassup.dinner.matching.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findBySlotIdOrderByIdAsc(Long slotId);

    List<DiningTable> findBySlotIdAndStatus(Long slotId, DiningTable.Status status);

    List<DiningTable> findByMatchRunId(Long matchRunId);

    List<DiningTable> findByStatusIn(Collection<DiningTable.Status> statuses);

    List<DiningTable> findAllByOrderByIdDesc();

    long countBySlotId(Long slotId);
}
