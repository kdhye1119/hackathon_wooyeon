package com.wassup.dinner.ops.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, Long> {

    Optional<ExceptionCase> findByDedupKey(String dedupKey);

    List<ExceptionCase> findByStatusOrderByIdDesc(ExceptionCase.Status status);

    List<ExceptionCase> findAllByOrderByIdDesc();

    long countByStatusAndType(ExceptionCase.Status status, ExceptionType type);
}
