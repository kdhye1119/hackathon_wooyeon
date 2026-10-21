package com.wassup.dinner.venue.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface VenuePhotoRepository extends JpaRepository<VenuePhoto, Long> {

    /** 사진 본문 없이 id 만 (목록·화면용) */
    @Query("select p.id from VenuePhoto p where p.venueId = :venueId order by p.id")
    List<Long> findIds(Long venueId);

    @Query("select p.venueId, p.id from VenuePhoto p where p.venueId in :venueIds order by p.id")
    List<Object[]> findIdsByVenues(Collection<Long> venueIds);
}
