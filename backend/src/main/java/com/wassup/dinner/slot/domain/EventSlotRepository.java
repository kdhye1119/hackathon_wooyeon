package com.wassup.dinner.slot.domain;

import com.wassup.dinner.user.domain.Options.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EventSlotRepository extends JpaRepository<EventSlot, Long> {

    List<EventSlot> findByStatusAndMatchAtLessThanEqual(SlotStatus status, LocalDateTime now);

    List<EventSlot> findByStatusOrderByDateAscStartTimeAsc(SlotStatus status);

    List<EventSlot> findByStatusIn(Collection<SlotStatus> statuses);

    List<EventSlot> findByDateInAndRegionIn(Collection<LocalDate> dates, Collection<Region> regions);

    List<EventSlot> findAllByOrderByDateDescStartTimeAscRegionAsc();

    /**
     * 모집 중 → 매칭 중 전환을 원자적으로 선점한다.
     * 스케줄러와 수동 실행이 겹쳐도 한 쪽만 1을 받으므로 중복 매칭이 생기지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EventSlot s set s.status = com.wassup.dinner.slot.domain.SlotStatus.MATCHING, s.version = s.version + 1 "
            + "where s.id = :id and s.status = com.wassup.dinner.slot.domain.SlotStatus.RECRUITING")
    int claimForMatching(@Param("id") Long id);
}
