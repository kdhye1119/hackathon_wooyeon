package com.wassup.dinner.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByApplicationId(Long applicationId);

    List<Refund> findAllByOrderByIdDesc();

    long countByStatus(Refund.Status status);
}
