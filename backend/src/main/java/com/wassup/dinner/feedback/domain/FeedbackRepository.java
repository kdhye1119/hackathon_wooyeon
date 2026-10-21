package com.wassup.dinner.feedback.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    boolean existsByApplicationId(Long applicationId);

    List<Feedback> findByTableId(Long tableId);

    List<Feedback> findByApplicationIdIn(Collection<Long> applicationIds);

    long countByUserId(Long userId);

    List<Feedback> findAllByOrderByIdDesc();
}
