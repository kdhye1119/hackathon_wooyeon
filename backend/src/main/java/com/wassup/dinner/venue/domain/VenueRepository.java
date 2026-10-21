package com.wassup.dinner.venue.domain;

import com.wassup.dinner.user.domain.Options.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findByRegionAndActiveTrue(Region region);
}
