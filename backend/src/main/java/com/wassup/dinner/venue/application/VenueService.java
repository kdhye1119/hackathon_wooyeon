package com.wassup.dinner.venue.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.user.domain.Options.PriceRange;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueBooking;
import com.wassup.dinner.venue.domain.VenueBookingRepository;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 확정 테이블에 지역·시간·인원·예산에 맞는 파트너 식당을 자동 배정한다. */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueBookingRepository bookingRepository;
    private final ExceptionCaseService exceptionCaseService;
    private final AppClock clock;

    /**
     * @param memberPriceRanges 참가자별 '식사에 쓸 수 있는 금액' 선택값
     * @return 배정된 식당. 같은 지역에 남은 식당이 없으면 운영자 예외(식당 부족)로 전환하고 empty.
     */
    @Transactional
    public Optional<Venue> assign(DiningTable table, EventSlot slot, Collection<Set<String>> memberPriceRanges) {
        Optional<VenueBooking> existing = bookingRepository.findByTableId(table.getId());
        if (existing.isPresent() && existing.get().getStatus() == VenueBooking.Status.BOOKED) {
            return venueRepository.findById(existing.get().getVenueId());
        }
        int budget = memberPriceRanges.stream().mapToInt(VenueService::maxLevel).min().orElse(3);
        List<Venue> candidates = venueRepository.findByRegionAndActiveTrue(slot.getRegion()).stream()
                .filter(v -> v.getSeatsPerTable() >= table.getMemberCount())
                .filter(v -> booked(v, slot) < v.getTablesPerSlot())
                .sorted(Comparator
                        // 모두가 감당 가능한 가격대 우선, 그 안에서는 예산에 가장 가까운 곳
                        .comparingInt((Venue v) -> level(v.getPriceRange()) <= budget ? 0 : 1)
                        .thenComparingInt(v -> Math.abs(budget - level(v.getPriceRange())))
                        .thenComparingLong(v -> booked(v, slot))
                        .thenComparingLong(Venue::getId))
                .toList();
        if (candidates.isEmpty()) {
            exceptionCaseService.open(ExceptionType.VENUE_SHORTAGE, "table:" + table.getId(),
                    slot.label() + " 테이블 " + table.getTableCode() + "(" + table.getMemberCount()
                            + "명)에 배정 가능한 파트너 식당이 없습니다.",
                    ExceptionCaseService.Refs.table(table.getId(), slot.getId()));
            return Optional.empty();
        }
        return Optional.of(book(table, slot, candidates.get(0)));
    }

    /** 운영자 수동 배정 (식당 부족 예외 처리) */
    @Transactional
    public Venue assignManually(DiningTable table, EventSlot slot, Long venueId) {
        Venue venue = venueRepository.findById(venueId).orElseThrow(() -> ApiException.notFound("식당"));
        bookingRepository.findByTableId(table.getId()).ifPresent(b -> {
            b.setStatus(VenueBooking.Status.CANCELED);
            bookingRepository.delete(b);
            bookingRepository.flush();
        });
        Venue v = book(table, slot, venue);
        exceptionCaseService.autoResolve(ExceptionType.VENUE_SHORTAGE, "table:" + table.getId(), "운영자 수동 배정: " + venue.getName());
        return v;
    }

    @Transactional
    public void release(DiningTable table) {
        bookingRepository.findByTableId(table.getId()).ifPresent(b -> b.setStatus(VenueBooking.Status.CANCELED));
    }

    private Venue book(DiningTable table, EventSlot slot, Venue venue) {
        String reservationName = "우연한식탁 " + table.getTableCode();
        bookingRepository.save(new VenueBooking(venue.getId(), slot.getId(), table.getId(), reservationName,
                table.getMemberCount(), clock.now()));
        table.setVenueId(venue.getId());
        table.setReservationName(reservationName);
        return venue;
    }

    private long booked(Venue v, EventSlot slot) {
        return bookingRepository.countByVenueIdAndSlotIdAndStatus(v.getId(), slot.getId(), VenueBooking.Status.BOOKED);
    }

    private static int level(PriceRange p) {
        return p == null ? 2 : p.ordinal() + 1;
    }

    /** 참가자가 쓸 수 있는 최대 가격 단계 (1: 1~2만, 2: 2~3만, 3: 3만 이상/상관없음) */
    private static int maxLevel(Set<String> ranges) {
        return ranges.stream().mapToInt(r -> PriceRange.valueOf(r).ordinal() + 1).max().orElse(3);
    }
}
