package com.wassup.dinner.venue.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenueBookingRepository extends JpaRepository<VenueBooking, Long> {

    long countByVenueIdAndSlotIdAndStatus(Long venueId, Long slotId, VenueBooking.Status status);

    Optional<VenueBooking> findByTableId(Long tableId);

    List<VenueBooking> findBySlotId(Long slotId);
}
