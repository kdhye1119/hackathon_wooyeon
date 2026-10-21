package com.wassup.dinner;

import com.wassup.dinner.application.application.ApplicationService;
import com.wassup.dinner.application.application.ApplicationService.SubmitRequest;
import com.wassup.dinner.application.application.ApplicationView;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.chat.domain.ChatMessage;
import com.wassup.dinner.chat.domain.ChatMessageRepository;
import com.wassup.dinner.chat.domain.ChatRoom;
import com.wassup.dinner.chat.domain.ChatRoomRepository;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.application.AttendanceService;
import com.wassup.dinner.feedback.application.FeedbackService;
import com.wassup.dinner.feedback.application.FeedbackService.FeedbackRequest;
import com.wassup.dinner.matching.application.MatchingService;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.notification.domain.Notification;
import com.wassup.dinner.notification.domain.NotificationRepository;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.ops.application.AutomationScheduler;
import com.wassup.dinner.ops.application.DemoService;
import com.wassup.dinner.ops.application.DemoService.DemoUser;
import com.wassup.dinner.ops.application.DemoService.ScenarioResponse;
import com.wassup.dinner.ops.domain.ExceptionCase;
import com.wassup.dinner.ops.domain.ExceptionCaseRepository;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.payment.application.PaymentService;
import com.wassup.dinner.payment.domain.PaymentRepository;
import com.wassup.dinner.payment.domain.TicketProduct;
import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options.Region;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** PRD 18장 해커톤 완료 조건 1~12 를 한 흐름으로 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class DemoScenarioIntegrationTest {

    @Autowired DemoService demoService;
    @Autowired MatchingService matchingService;
    @Autowired ApplicationService applicationService;
    @Autowired PaymentService paymentService;
    @Autowired AttendanceService attendanceService;
    @Autowired FeedbackService feedbackService;
    @Autowired AutomationScheduler scheduler;
    @Autowired SlotService slotService;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired DiningTableRepository tableRepository;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired ChatMessageRepository messageRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ExceptionCaseRepository exceptionRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EventSlotRepository slotRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppClock clock;

    @AfterEach
    void resetClock() {
        clock.setOffset(Duration.ZERO);
    }

    @Test
    void 신청부터_다음_매칭까지_운영자_개입_없이_완료된다() {
        // 1. 서로 다른 프로필의 참가자들이 날짜·지역을 선택하고 신청·결제
        ScenarioResponse scenario = demoService.scenario(42);
        Long hongdae = scenario.slots().get(0).id();
        Long jamsil = scenario.slots().get(1).id();
        assertThat(scenario.participants()).hasSize(19).allSatisfy(u -> assertThat(u.paymentStatus()).isEqualTo("PAID"));
        EventSlot slot = slotRepository.findById(hongdae).orElseThrow();

        // 2. 매칭 마감 시각이 되면 스케줄러가 자동 매칭 실행 (시간 이동 + tick)
        clock.travelTo(slot.getMatchAt().plusMinutes(1));
        AutomationScheduler.TickResult tick = scheduler.tick(MatchRun.Trigger.SCHEDULER);
        assertThat(tick.matchRunId()).isNotNull();
        // 같은 작업을 다시 실행해도 중복 테이블이 생기지 않는다
        assertThat(matchingService.run(List.of(hongdae, jamsil), MatchRun.Trigger.ADMIN)).isEmpty();

        // 3~5. 4~6인 테이블 생성, 4명 미만 회차(잠실)는 재배치, 조건 통과 테이블 자동 확정
        List<DiningTable> tables = tableRepository.findBySlotIdOrderByIdAsc(hongdae);
        assertThat(tables).isNotEmpty().allSatisfy(t -> {
            assertThat(t.getMemberCount()).isBetween(4, 6);
            assertThat(t.ageGap()).isLessThanOrEqualTo(8);
            assertThat(t.isAutoConfirmed()).isTrue();
        });
        assertThat(tableRepository.findBySlotIdOrderByIdAsc(jamsil)).isEmpty();
        Map<Long, Application> byUser = scenario.participants().stream()
                .collect(Collectors.toMap(DemoUser::userId, u -> applicationRepository.findById(u.applicationId()).orElseThrow()));
        List<Application> jamsilFirst = scenario.participants().subList(14, 17).stream().map(u -> byUser.get(u.userId())).toList();
        assertThat(jamsilFirst).allSatisfy(a -> {
            assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
            assertThat(a.getAssignedSlotId()).isEqualTo(hongdae);
            assertThat(a.getReallocationCount()).isPositive();
        });

        // 6~7. 식당 자동 배정 + 앱 단체방 생성 + 공지(큐레이션·취소 정책)
        for (DiningTable t : tables) {
            assertThat(t.getVenueId()).isNotNull();
            ChatRoom room = roomRepository.findByTableId(t.getId()).orElseThrow();
            List<ChatMessage> msgs = messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(room.getId(), 0L);
            assertThat(msgs).anySatisfy(m -> {
                assertThat(m.getType()).isEqualTo(ChatMessage.Type.NOTICE);
                assertThat(m.getContent()).contains("큐레이션").contains("환불");
            });
        }

        // 8. 매칭 실패자(나이 차이 초과)에게 대체 일정 추천 → 일정 이동 / 환불 자동 처리
        Application old = byUser.get(scenario.participants().get(17).userId());
        Application young = byUser.get(scenario.participants().get(18).userId());
        assertThat(old.getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);
        assertThat(old.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_AVAILABLE);
        ApplicationView offered = applicationService.detail(old.getUserId(), old.getId());
        assertThat(offered.nextActions()).contains("CHOOSE_RESOLUTION");
        // 사용자가 고른 지역(잠실)의 다른 일정을 먼저 추천한다
        assertThat(offered.resolution().alternatives()).isNotEmpty().first()
                .satisfies(s -> assertThat(s.region()).isEqualTo("JAMSIL"));
        Long transferTo = offered.resolution().alternatives().get(0).id();

        long paymentsBefore = paymentRepository.count();
        ApplicationView moved = applicationService.resolve(old.getUserId(), old.getId(), MatchResolution.Choice.TRANSFER, transferTo);
        assertThat(moved.matchStatus()).isEqualTo("TRANSFERRED");
        Application newApp = applicationRepository.findById(moved.transferredToId()).orElseThrow();
        assertThat(newApp.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(newApp.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore); // 재결제(중복 승인) 없음

        ApplicationView refunded = applicationService.resolve(young.getUserId(), young.getId(), MatchResolution.Choice.REFUND, null);
        assertThat(refunded.paymentStatus()).isEqualTo("REFUNDED");
        // 같은 선택을 다시 눌러도 중복 환불 없음
        applicationService.resolve(young.getUserId(), young.getId(), MatchResolution.Choice.REFUND, null);
        assertThat(paymentRepository.findById(young.getPaymentId()).orElseThrow().getRefundedAmount()).isEqualTo(9_900);

        // 9. 행사 1일 전 리마인드, 행사 시작 시 대화 콘텐츠 자동 게시
        clock.travelTo(slot.startAt().minusDays(1).plusMinutes(1));
        scheduler.tick(MatchRun.Trigger.SCHEDULER);
        clock.travelTo(slot.startAt().plusMinutes(5));
        scheduler.tick(MatchRun.Trigger.SCHEDULER);
        for (DiningTable t : tables) {
            ChatRoom room = roomRepository.findByTableId(t.getId()).orElseThrow();
            Set<ChatMessage.Type> types = messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(room.getId(), 0L)
                    .stream().map(ChatMessage::getType).collect(Collectors.toSet());
            assertThat(types).contains(ChatMessage.Type.REMINDER, ChatMessage.Type.CONTENT);
        }
        assertThat(notificationRepository.findAll()).anySatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.EVENT_REMINDER);
            assertThat(n.getStatus()).isEqualTo(Notification.Status.SENT);
        });

        // 10. 체크인 (한 명만 미체크인 → 노쇼 후보) → 종료 후 피드백
        DiningTable first = tables.get(0);
        List<Application> seated = applicationRepository.findByTableId(first.getId());
        Application noShow = seated.get(seated.size() - 1);
        seated.stream().filter(a -> a != noShow).forEach(a -> attendanceService.checkIn(a.getUserId(), a.getId()));
        tables.stream().skip(1).flatMap(t -> applicationRepository.findByTableId(t.getId()).stream())
                .forEach(a -> attendanceService.checkIn(a.getUserId(), a.getId()));
        clock.travelTo(slot.endAt().plusMinutes(1));
        scheduler.tick(MatchRun.Trigger.SCHEDULER);
        assertThat(applicationRepository.findById(noShow.getId()).orElseThrow().getAttendanceStatus()).isEqualTo(AttendanceStatus.NO_SHOW);

        Application rater = seated.get(0);
        Long avoid = seated.get(1).getUserId();
        Long want = seated.get(2).getUserId();
        feedbackService.submit(rater.getUserId(), rater.getId(),
                new FeedbackRequest(5, 4, 4, 5, 9, "좋았어요", List.of(want), List.of(avoid), true));

        // 11. 다음 매칭에 이전 만남·피드백 반영: 비선호 관계는 분리, 이전 만남은 최소화
        List<Long> attendedUsers = tables.stream().flatMap(t -> applicationRepository.findByTableId(t.getId()).stream())
                .filter(a -> a.getAttendanceStatus() == AttendanceStatus.ATTENDED).map(Application::getUserId).toList();
        var nextDate = clock.today().plusDays(5);
        while (!slotRepository.findByDateInAndRegionIn(List.of(nextDate), List.of(Region.EULJIRO_JONGNO)).isEmpty()) {
            nextDate = nextDate.plusDays(1);
        }
        var next = slotService.createBulk(new SlotService.BulkSlotRequest(List.of(nextDate),
                List.of(Region.EULJIRO_JONGNO), LocalTime.of(19, 30), 24)).get(0);
        for (Long userId : attendedUsers) {
            var res = applicationService.submit(userId, new SubmitRequest(List.of(next.date()), List.of(Region.EULJIRO_JONGNO),
                    null, TicketProduct.SINGLE, null));
            paymentService.confirm(userId, res.paymentId(), false);
        }
        matchingService.run(List.of(next.id()), MatchRun.Trigger.ADMIN).orElseThrow();
        List<DiningTable> nextTables = tableRepository.findBySlotIdOrderByIdAsc(next.id());
        assertThat(nextTables).isNotEmpty();
        for (DiningTable t : nextTables) {
            Set<Long> members = applicationRepository.findByTableId(t.getId()).stream().map(Application::getUserId).collect(Collectors.toSet());
            assertThat(members.contains(rater.getUserId()) && members.contains(avoid)).as("비선호 관계 분리").isFalse();
        }
        int metPairs = nextTables.stream().mapToInt(DiningTable::getMetBeforePairs).sum();
        int maxPossible = tables.stream().mapToInt(t -> t.getMemberCount() * (t.getMemberCount() - 1) / 2).sum();
        assertThat(metPairs).as("새로운 사람 우선").isLessThan(maxPossible / 2);

        // 12. 해결되지 않은 예외만 운영자 화면에: 노쇼 후보 외에는 없음
        List<ExceptionCase> open = exceptionRepository.findByStatusOrderByIdDesc(ExceptionCase.Status.OPEN);
        assertThat(open).extracting(ExceptionCase::getType).containsOnly(ExceptionType.NO_SHOW_REVIEW);
        User u = userRepository.findById(rater.getUserId()).orElseThrow();
        assertThat(u.getProfile().getBirthYear()).isNotNull();
    }
}
