package com.wassup.dinner.feedback.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.application.domain.AttendanceStatus;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.slot.domain.SlotStatus;
import com.wassup.dinner.user.domain.Options.Region;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/** 참가 이력과 다음 일정 추천 (RET-01, RET-02). */
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final ApplicationRepository applicationRepository;
    private final EventSlotRepository slotRepository;
    private final DiningTableRepository tableRepository;
    private final VenueRepository venueRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationService notificationService;
    private final AppClock clock;

    public record HistoryItem(Long applicationId, Long slotId, String slotLabel, String region, LocalDateTime startAt,
                              String tableCode, String venueName, String attendanceStatus, boolean feedbackSubmitted,
                              boolean feedbackAvailable) {
    }

    public record Recommendation(Long slotId, String label, String region, LocalDateTime startAt,
                                 LocalDateTime applyDeadline, String reason) {
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(Long userId) {
        List<HistoryItem> items = new ArrayList<>();
        for (Application a : applicationRepository.findByUserIdAndTableIdIsNotNullOrderByIdDesc(userId)) {
            EventSlot slot = slotRepository.findById(a.getAssignedSlotId()).orElse(null);
            DiningTable t = tableRepository.findById(a.getTableId()).orElse(null);
            if (slot == null || t == null) {
                continue;
            }
            String venue = t.getVenueId() == null ? null : venueRepository.findById(t.getVenueId()).map(Venue::getName).orElse(null);
            boolean submitted = feedbackRepository.existsByApplicationId(a.getId());
            items.add(new HistoryItem(a.getId(), slot.getId(), slot.label(), slot.getRegion().getLabel(), slot.startAt(),
                    t.getTableCode(), venue, a.getAttendanceStatus() == null ? null : a.getAttendanceStatus().name(),
                    submitted, a.getAttendanceStatus() == AttendanceStatus.ATTENDED && !submitted));
        }
        return items;
    }

    /** 이전 참가 지역·요일 선호를 바탕으로 아직 신청하지 않은 모집 회차를 추천 */
    @Transactional(readOnly = true)
    public List<Recommendation> recommend(Long userId) {
        List<Application> mine = applicationRepository.findByUserIdOrderByIdDesc(userId);
        Map<Region, Long> regionFreq = new EnumMap<>(Region.class);
        Map<DayOfWeek, Long> dayFreq = new EnumMap<>(DayOfWeek.class);
        Set<Long> active = new HashSet<>();
        for (Application a : mine) {
            if (a.isActive()) {
                active.addAll(a.getSlotIds());
            }
            boolean attended = a.getAttendanceStatus() == AttendanceStatus.ATTENDED;
            List<Long> ids = a.getAssignedSlotId() != null ? List.of(a.getAssignedSlotId()) : a.getSlotIds();
            for (EventSlot s : slotRepository.findAllById(ids)) {
                regionFreq.merge(s.getRegion(), attended ? 2L : 1L, Long::sum);
                dayFreq.merge(s.getDate().getDayOfWeek(), attended ? 2L : 1L, Long::sum);
            }
        }
        LocalDateTime now = clock.now();
        return slotRepository.findByStatusOrderByDateAscStartTimeAsc(SlotStatus.RECRUITING).stream()
                .filter(s -> s.isOpen(now) && !active.contains(s.getId()))
                .sorted(Comparator.comparingLong((EventSlot s) -> -(2 * regionFreq.getOrDefault(s.getRegion(), 0L)
                                + dayFreq.getOrDefault(s.getDate().getDayOfWeek(), 0L)))
                        .thenComparing(EventSlot::startAt))
                .limit(5)
                .map(s -> new Recommendation(s.getId(), s.label(), s.getRegion().getLabel(), s.startAt(), s.getApplyDeadline(),
                        reason(s, regionFreq, dayFreq)))
                .toList();
    }

    private static String reason(EventSlot s, Map<Region, Long> regionFreq, Map<DayOfWeek, Long> dayFreq) {
        List<String> parts = new ArrayList<>();
        if (regionFreq.getOrDefault(s.getRegion(), 0L) > 0) {
            parts.add("자주 찾으신 " + s.getRegion().getLabel());
        }
        if (dayFreq.getOrDefault(s.getDate().getDayOfWeek(), 0L) > 0) {
            parts.add(s.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN) + " 저녁");
        }
        String base = parts.isEmpty() ? "가장 가까운 모집 일정이에요." : String.join(" · ", parts) + " 일정이에요.";
        return base + " 지난 테이블에서 만난 분들과 겹치지 않게 새로운 분들로 구성해 드려요.";
    }

    /** 새 회차가 열리면 그 지역에 참석 이력이 있는 사람에게 추천 알림 (회차당 1회) */
    @Transactional
    public int announceNewSlots(Collection<EventSlot> slots) {
        if (slots.isEmpty()) {
            return 0;
        }
        Map<Long, Set<Region>> attendedRegions = new HashMap<>();
        for (Application a : applicationRepository.findAll()) {
            if (a.getAttendanceStatus() == AttendanceStatus.ATTENDED && a.getAssignedSlotId() != null) {
                slotRepository.findById(a.getAssignedSlotId()).ifPresent(s ->
                        attendedRegions.computeIfAbsent(a.getUserId(), k -> new HashSet<>()).add(s.getRegion()));
            }
        }
        int sent = 0;
        for (EventSlot slot : slots) {
            for (Map.Entry<Long, Set<Region>> e : attendedRegions.entrySet()) {
                if (e.getValue().contains(slot.getRegion())) {
                    notificationService.notify(e.getKey(), NotificationType.NEXT_RECOMMENDATION,
                            NotificationService.vars("slot", slot.label(), "reason", "지난번 즐거웠던 " + slot.getRegion().getLabel() + "에서 새로운 분들을 만나보세요."),
                            "slot:" + slot.getId() + ":user:" + e.getKey(), null, null);
                    sent++;
                }
            }
        }
        return sent;
    }
}
