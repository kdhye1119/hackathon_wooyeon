package com.wassup.dinner.matching.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRunRepository extends JpaRepository<MatchRun, Long> {

    List<MatchRun> findAllByOrderByIdDesc();
}
