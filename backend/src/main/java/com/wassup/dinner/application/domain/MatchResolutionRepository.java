package com.wassup.dinner.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchResolutionRepository extends JpaRepository<MatchResolution, Long> {

    Optional<MatchResolution> findByApplicationId(Long applicationId);

    long countByStatus(MatchResolution.Status status);

    java.util.List<MatchResolution> findByStatus(MatchResolution.Status status);
}
