package com.wassup.dinner.matching.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatOfferRepository extends JpaRepository<SeatOffer, Long> {

    List<SeatOffer> findByTableId(Long tableId);

    Optional<SeatOffer> findFirstByApplicationIdAndStatus(Long applicationId, SeatOffer.Status status);

    boolean existsByTableIdAndStatus(Long tableId, SeatOffer.Status status);

    List<SeatOffer> findByStatusAndExpiresAtLessThanEqual(SeatOffer.Status status, LocalDateTime now);
}
