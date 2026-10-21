package com.wassup.dinner;

import com.wassup.dinner.application.application.ApplicationService;
import com.wassup.dinner.application.domain.*;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.application.MatchingService;
import com.wassup.dinner.matching.application.TableService;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.matching.domain.SeatOffer;
import com.wassup.dinner.matching.domain.SeatOfferRepository;
import com.wassup.dinner.ops.application.DemoService;
import com.wassup.dinner.ops.application.DemoService.DemoUser;
import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** STA-07 취소석 대기자 제안(수락/거절/만료) + MAT-14 운영자 긴급 수정(이동·분리·병합·해제) */
@SpringBootTest
@ActiveProfiles("test")
class SeatOfferAndAdminEditTest {

    @Autowired DemoService demoService;
    @Autowired SlotService slotService;
    @Autowired MatchingService matchingService;
    @Autowired ApplicationService applicationService;
    @Autowired TableService tableService;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired DiningTableRepository tableRepository;
    @Autowired SeatOfferRepository seatOfferRepository;
    @Autowired EventSlotRepository slotRepository;
    @Autowired AppClock clock;

    @AfterEach
    void resetClock() {
        clock.setOffset(Duration.ZERO);
    }

    /** 비슷한 나이 7명 → 6명 테이블 1개 + 대기자 1명 */
    private record Setup(Long slotId, DiningTable table, List<DemoUser> users, Application waiter) {
    }

    private Setup sevenPeople(long seed, Region region) {
        var date = clock.today().plusDays(6);
        while (!slotRepository.findByDateInAndRegionIn(List.of(date), List.of(region)).isEmpty()) {
            date = date.plusDays(1);
        }
        var slot = slotService.createBulk(new SlotService.BulkSlotRequest(List.of(date), List.of(region), LocalTime.of(19, 30), 24)).get(0);
        List<DemoUser> users = demoService.seed(new DemoService.SeedRequest(List.of(date), List.of(region), 7, 1995, 1997, true, null, seed));
        matchingService.run(List.of(slot.id()), MatchRun.Trigger.ADMIN).orElseThrow();
        List<DiningTable> tables = tableRepository.findBySlotIdOrderByIdAsc(slot.id());
        assertThat(tables).hasSize(1);
        Application waiter = users.stream().map(u -> applicationRepository.findById(u.applicationId()).orElseThrow())
                .filter(a -> a.getMatchStatus() == MatchStatus.ALTERNATIVE_OFFERED).findFirst().orElseThrow();
        return new Setup(slot.id(), tables.get(0), users, waiter);
    }

    private List<Application> seated(DiningTable t) {
        return applicationRepository.findByTableId(t.getId()).stream().filter(Application::isSeated).toList();
    }

    @Test
    void 취소석이_나면_대기자에게_묻고_수락하면_합류한다() {
        Setup s = sevenPeople(11, Region.HONGDAE_HAPJEONG);
        Application leaving = seated(s.table()).get(0);
        applicationService.cancel(leaving.getUserId(), leaving.getId());

        SeatOffer offer = seatOfferRepository.findFirstByApplicationIdAndStatus(s.waiter().getId(), SeatOffer.Status.PENDING).orElseThrow();
        assertThat(offer.getTableId()).isEqualTo(s.table().getId());
        // 아직 수락 전: 대기자는 테이블에 없음
        assertThat(applicationRepository.findById(s.waiter().getId()).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.ALTERNATIVE_OFFERED);

        tableService.respondSeatOffer(s.waiter().getUserId(), s.waiter().getId(), true);
        Application joined = applicationRepository.findById(s.waiter().getId()).orElseThrow();
        assertThat(joined.getMatchStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(joined.getTableId()).isEqualTo(s.table().getId());
        assertThat(seated(s.table())).hasSize(6);
    }

    @Test
    void 거절하거나_기한이_지나면_다음_대기자로_넘어가고_없으면_종료한다() {
        Setup s = sevenPeople(12, Region.JAMSIL);
        Application leaving = seated(s.table()).get(0);
        applicationService.cancel(leaving.getUserId(), leaving.getId());
        tableService.respondSeatOffer(s.waiter().getUserId(), s.waiter().getId(), false);
        SeatOffer declined = seatOfferRepository.findByTableId(s.table().getId()).get(0);
        assertThat(declined.getStatus()).isEqualTo(SeatOffer.Status.DECLINED);
        // 다른 대기자가 없으니 새 제안 없음, 5명이라 테이블은 유지
        assertThat(seatOfferRepository.existsByTableIdAndStatus(s.table().getId(), SeatOffer.Status.PENDING)).isFalse();
        assertThat(tableRepository.findById(s.table().getId()).orElseThrow().getStatus()).isEqualTo(DiningTable.Status.CONFIRMED);
    }

    @Test
    void 운영자는_참가자를_분리하고_다시_병합하고_테이블을_해제할_수_있다() {
        Setup s = sevenPeople(13, Region.GANGNAM);
        List<Application> members = seated(s.table());

        // 분리: 한 명을 새 테이블로
        DiningTable split = tableService.moveMember(members.get(0).getId(), null, "안전 문제 분리", "ADMIN:1");
        assertThat(split.getId()).isNotEqualTo(s.table().getId());
        assertThat(seated(split)).hasSize(1);
        assertThat(seated(s.table())).hasSize(5);

        // 병합: 새 테이블 인원을 원래 테이블로 → 빈 테이블은 해제
        tableService.moveMember(members.get(0).getId(), s.table().getId(), "다시 합치기", "ADMIN:1");
        assertThat(seated(s.table())).hasSize(6);
        assertThat(tableRepository.findById(split.getId()).orElseThrow().getStatus()).isEqualTo(DiningTable.Status.DISSOLVED);

        // 해제: 같은 회차에 다른 테이블이 없으니 모두 대체 일정 안내
        tableService.dissolveByAdmin(s.table().getId(), "식당 사정", "ADMIN:1");
        assertThat(tableRepository.findById(s.table().getId()).orElseThrow().getStatus()).isEqualTo(DiningTable.Status.DISSOLVED);
        assertThat(members.stream().map(a -> applicationRepository.findById(a.getId()).orElseThrow().getMatchStatus()))
                .allMatch(st -> st == MatchStatus.ALTERNATIVE_OFFERED || st == MatchStatus.NO_MATCH);
    }
}
