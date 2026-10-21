package com.wassup.dinner.admin.api;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.application.RetentionService;
import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.application.SlotService.BulkSlotRequest;
import com.wassup.dinner.slot.application.SlotService.SlotDto;
import com.wassup.dinner.slot.application.SlotService.SlotRequest;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options.PriceRange;
import com.wassup.dinner.user.domain.Options.Region;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueBookingRepository;
import com.wassup.dinner.venue.domain.VenueRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Slots & Venues", description = "회차 생성·관리, 식당 재고 관리")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSlotVenueController {

    private final SlotService slotService;
    private final EventSlotRepository slotRepository;
    private final RetentionService retentionService;
    private final VenueRepository venueRepository;
    private final VenueBookingRepository bookingRepository;
    private final AppClock clock;

    public record VenueRequest(@NotBlank String name, @NotNull Region region, @NotBlank String address, String mapUrl,
                               String phone, @NotNull PriceRange priceRange, String priceGuide,
                               @Min(4) Integer seatsPerTable, @Min(0) Integer tablesPerSlot, Boolean active) {
    }

    public record VenueDto(Long id, String name, String region, String regionLabel, String address, String mapUrl,
                           String phone, String priceRange, String priceGuide, int seatsPerTable, int tablesPerSlot,
                           boolean active) {
        static VenueDto of(Venue v) {
            return new VenueDto(v.getId(), v.getName(), v.getRegion().name(), v.getRegion().getLabel(), v.getAddress(),
                    v.getMapUrl(), v.getPhone(), v.getPriceRange().name(), v.getPriceGuide(), v.getSeatsPerTable(),
                    v.getTablesPerSlot(), v.isActive());
        }
    }

    public record VenueAvailability(Long venueId, String name, Long slotId, int capacity, long booked) {
    }

    // ---- 회차

    @Operation(summary = "회차 목록 (전체)")
    @GetMapping("/slots")
    public List<SlotDto> slots() {
        return slotService.all();
    }

    @Operation(summary = "회차 생성 (마감·매칭 시각 생략 시 행사 2일 전 10:00 / 12:00). 이전 참석자에게 추천 알림")
    @PostMapping("/slots")
    public SlotDto create(@RequestBody SlotRequest request) {
        SlotDto dto = slotService.create(request);
        retentionService.announceNewSlots(List.of(slotRepository.findById(dto.id()).orElseThrow()));
        return dto;
    }

    @Operation(summary = "회차 일괄 생성 (날짜 × 지역)")
    @PostMapping("/slots/bulk")
    public List<SlotDto> createBulk(@RequestBody BulkSlotRequest request) {
        List<SlotDto> created = slotService.createBulk(request);
        retentionService.announceNewSlots(slotRepository.findAllById(created.stream().map(SlotDto::id).toList()));
        return created;
    }

    @Operation(summary = "회차 수정 (정원·시간·신청 마감·자동 매칭 실행 시각)")
    @PutMapping("/slots/{id}")
    public SlotDto update(@PathVariable Long id, @RequestBody SlotRequest request) {
        return slotService.update(id, request);
    }

    // ---- 식당

    @Operation(summary = "식당 목록")
    @GetMapping("/venues")
    public List<VenueDto> venues() {
        return venueRepository.findAll().stream().map(VenueDto::of).toList();
    }

    @Operation(summary = "식당 등록")
    @PostMapping("/venues")
    @Transactional
    public VenueDto createVenue(@Valid @RequestBody VenueRequest r) {
        Venue v = new Venue(r.name(), r.region(), r.address(), r.mapUrl(), r.priceRange(), r.priceGuide(),
                r.tablesPerSlot() == null ? 2 : r.tablesPerSlot(), clock.now());
        apply(v, r);
        return VenueDto.of(venueRepository.save(v));
    }

    @Operation(summary = "식당 수정 (회차당 수용 테이블 수·활성 여부 등)")
    @PutMapping("/venues/{id}")
    @Transactional
    public VenueDto updateVenue(@PathVariable Long id, @Valid @RequestBody VenueRequest r) {
        Venue v = venueRepository.findById(id).orElseThrow(() -> ApiException.notFound("식당"));
        v.setName(r.name());
        v.setRegion(r.region());
        v.setAddress(r.address());
        v.setMapUrl(r.mapUrl());
        v.setPriceRange(r.priceRange());
        v.setPriceGuide(r.priceGuide());
        if (r.tablesPerSlot() != null) v.setTablesPerSlot(r.tablesPerSlot());
        apply(v, r);
        return VenueDto.of(v);
    }

    @Operation(summary = "회차별 식당 예약 현황 (재고)")
    @GetMapping("/slots/{slotId}/venue-availability")
    @Transactional(readOnly = true)
    public List<VenueAvailability> availability(@PathVariable Long slotId) {
        var slot = slotRepository.findById(slotId).orElseThrow(() -> ApiException.notFound("회차"));
        return venueRepository.findByRegionAndActiveTrue(slot.getRegion()).stream()
                .map(v -> new VenueAvailability(v.getId(), v.getName(), slotId, v.getTablesPerSlot(),
                        bookingRepository.countByVenueIdAndSlotIdAndStatus(v.getId(), slotId,
                                com.wassup.dinner.venue.domain.VenueBooking.Status.BOOKED)))
                .toList();
    }

    private static void apply(Venue v, VenueRequest r) {
        v.setPhone(r.phone());
        if (r.seatsPerTable() != null) v.setSeatsPerTable(r.seatsPerTable());
        if (r.active() != null) v.setActive(r.active());
    }
}
