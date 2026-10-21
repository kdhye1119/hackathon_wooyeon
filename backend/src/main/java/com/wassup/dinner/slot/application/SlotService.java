package com.wassup.dinner.slot.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.slot.domain.SlotStatus;
import com.wassup.dinner.user.domain.Options.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final EventSlotRepository slotRepository;
    private final ApplicationRepository applicationRepository;
    private final AppClock clock;

    public record SlotDto(Long id, LocalDate date, LocalTime startTime, String region, String regionLabel, String label,
                          int capacity, long applicants, LocalDateTime applyDeadline, LocalDateTime matchAt,
                          LocalDateTime refundDeadline, String status, boolean open) {
    }

    /** 신청서 화면용: 모집 중인 날짜·지역 목록 */
    public record OpenSlotsResponse(List<DateOption> dates, List<RegionOption> regions, List<SlotDto> slots) {
        public record DateOption(LocalDate date, String label, LocalDateTime applyDeadline, LocalDateTime matchAt) {
        }

        public record RegionOption(String code, String label) {
        }
    }

    public record SlotRequest(LocalDate date, LocalTime startTime, Region region, Integer capacity,
                              LocalDateTime applyDeadline, LocalDateTime matchAt) {
    }

    public record BulkSlotRequest(List<LocalDate> dates, List<Region> regions, LocalTime startTime, Integer capacity) {
    }

    @Transactional(readOnly = true)
    public OpenSlotsResponse openSlots() {
        LocalDateTime now = clock.now();
        List<EventSlot> open = slotRepository.findByStatusOrderByDateAscStartTimeAsc(SlotStatus.RECRUITING).stream()
                .filter(s -> s.isOpen(now)).toList();
        Map<Long, Long> counts = activeCounts();
        List<OpenSlotsResponse.DateOption> dates = open.stream()
                .collect(Collectors.toMap(EventSlot::getDate, s -> s, (a, b) -> a, TreeMap::new)).values().stream()
                .map(s -> new OpenSlotsResponse.DateOption(s.getDate(),
                        s.label().replace(" " + s.getRegion().getLabel(), ""), s.getApplyDeadline(), s.getMatchAt()))
                .toList();
        List<OpenSlotsResponse.RegionOption> regions = open.stream().map(EventSlot::getRegion).distinct().sorted()
                .map(r -> new OpenSlotsResponse.RegionOption(r.name(), r.getLabel())).toList();
        return new OpenSlotsResponse(dates, regions, open.stream().map(s -> toDto(s, counts, now)).toList());
    }

    @Transactional(readOnly = true)
    public List<SlotDto> all() {
        LocalDateTime now = clock.now();
        Map<Long, Long> counts = activeCounts();
        return slotRepository.findAllByOrderByDateDescStartTimeAscRegionAsc().stream().map(s -> toDto(s, counts, now)).toList();
    }

    @Transactional
    public SlotDto create(SlotRequest r) {
        EventSlot s = slotRepository.save(build(r.date(), r.startTime(), r.region(), r.capacity(), r.applyDeadline(), r.matchAt()));
        return toDto(s, Map.of(), clock.now());
    }

    /** 목요일 회차 일괄 생성: 날짜 × 지역 */
    @Transactional
    public List<SlotDto> createBulk(BulkSlotRequest r) {
        Set<String> existing = slotRepository.findByDateInAndRegionIn(r.dates(), r.regions()).stream()
                .map(s -> s.getDate() + "|" + s.getRegion()).collect(Collectors.toSet());
        List<SlotDto> created = new ArrayList<>();
        for (LocalDate d : r.dates()) {
            for (Region region : r.regions()) {
                if (existing.contains(d + "|" + region)) {
                    continue;
                }
                EventSlot s = slotRepository.save(build(d, r.startTime(), region, r.capacity(), null, null));
                created.add(toDto(s, Map.of(), clock.now()));
            }
        }
        return created;
    }

    @Transactional
    public SlotDto update(Long id, SlotRequest r) {
        EventSlot s = slotRepository.findById(id).orElseThrow(() -> ApiException.notFound("회차"));
        if (s.getStatus() != SlotStatus.RECRUITING) {
            throw new ApiException(ErrorCode.INVALID_STATE, "모집 중인 회차만 수정할 수 있습니다.");
        }
        if (r.capacity() != null) s.setCapacity(r.capacity());
        if (r.startTime() != null) s.setStartTime(r.startTime());
        if (r.applyDeadline() != null) s.setApplyDeadline(r.applyDeadline());
        if (r.matchAt() != null) s.setMatchAt(r.matchAt());
        return toDto(s, activeCounts(), clock.now());
    }

    /**
     * 기본 일정: 신청 마감·자동 매칭 = 행사 2일 전 24:00 (환불 불가 시점과 같게)
     * 매칭 직후 단체방·식당 안내가 나가 현재 운영처럼 행사 2일 전에 안내된다.
     */
    private EventSlot build(LocalDate date, LocalTime startTime, Region region, Integer capacity,
                            LocalDateTime applyDeadline, LocalDateTime matchAt) {
        if (date == null || region == null) {
            throw new IllegalArgumentException("날짜와 지역은 필수입니다.");
        }
        LocalTime start = startTime == null ? LocalTime.of(19, 30) : startTime;
        // 기본: 신청 마감 = 자동 매칭 = 환불 불가 시점 = 행사 2일 전 24:00 (FAQ Q1)
        LocalDateTime deadline = applyDeadline == null ? date.minusDays(1).atStartOfDay() : applyDeadline;
        LocalDateTime match = matchAt == null ? deadline : matchAt;
        if (match.isBefore(deadline)) {
            throw new IllegalArgumentException("자동 매칭 시각은 신청 마감 이후여야 합니다.");
        }
        return new EventSlot(date, start, region, capacity == null ? 24 : capacity, deadline, match, clock.now());
    }

    public Map<Long, Long> activeCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Application a : applicationRepository.findAll()) {
            if (!a.isActive()) {
                continue;
            }
            List<Long> ids = a.getAssignedSlotId() != null ? List.of(a.getAssignedSlotId()) : a.getSlotIds();
            ids.forEach(id -> counts.merge(id, 1L, Long::sum));
        }
        return counts;
    }

    public static SlotDto toDto(EventSlot s, Map<Long, Long> counts, LocalDateTime now) {
        return new SlotDto(s.getId(), s.getDate(), s.getStartTime(), s.getRegion().name(), s.getRegion().getLabel(),
                s.label(), s.getCapacity(), counts.getOrDefault(s.getId(), 0L), s.getApplyDeadline(), s.getMatchAt(),
                s.refundDeadline(), s.getStatus().name(), s.isOpen(now));
    }
}
