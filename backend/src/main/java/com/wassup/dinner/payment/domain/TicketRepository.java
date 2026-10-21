package com.wassup.dinner.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserIdOrderByIdDesc(Long userId);

    Optional<Ticket> findByPaymentId(Long paymentId);
}
