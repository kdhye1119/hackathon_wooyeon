package com.wassup.dinner.safety.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SafetyReportRepository extends JpaRepository<SafetyReport, Long> {

    List<SafetyReport> findByReporterIdInOrReportedIdIn(Collection<Long> reporters, Collection<Long> reported);

    List<SafetyReport> findByReportedId(Long reportedId);

    List<SafetyReport> findAllByOrderByIdDesc();

    long countByStatus(SafetyReport.Status status);
}
