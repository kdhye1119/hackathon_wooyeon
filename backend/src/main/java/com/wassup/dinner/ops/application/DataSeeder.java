package com.wassup.dinner.ops.application;

import com.wassup.dinner.common.auth.PasswordHasher;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.matching.application.MatchingPolicyService;
import com.wassup.dinner.notification.domain.MessageTemplate;
import com.wassup.dinner.notification.domain.MessageTemplateRepository;
import com.wassup.dinner.notification.domain.NotificationType;
import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.AdminAccount;
import com.wassup.dinner.user.domain.AdminAccountRepository;
import com.wassup.dinner.user.domain.Options.PriceRange;
import com.wassup.dinner.user.domain.Options.Region;
import com.wassup.dinner.venue.domain.Venue;
import com.wassup.dinner.venue.domain.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 최초 기동 시 기본 데이터: 운영자 계정, 매칭 정책, 알림 템플릿, 파트너 식당, 다가오는 목요일 회차. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final AdminAccountRepository adminRepository;
    private final PasswordHasher passwordHasher;
    private final MatchingPolicyService policyService;
    private final MessageTemplateRepository templateRepository;
    private final VenueRepository venueRepository;
    private final EventSlotRepository slotRepository;
    private final SlotService slotService;
    private final AppClock clock;

    @Value("${app.seed.admin-password:admin1234}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.findByLoginId("admin").isEmpty()) {
            adminRepository.save(new AdminAccount("admin", passwordHasher.hash(adminPassword), "와썹하우스 운영자", clock.now()));
            log.info("운영자 계정 생성: admin / {}", adminPassword);
        }
        policyService.get();
        for (NotificationType t : NotificationType.values()) {
            if (templateRepository.findByCode(t.name()).isEmpty()) {
                templateRepository.save(new MessageTemplate(t.name(), t.getDefaultTitle(), t.getDefaultBody(), clock.now()));
            }
        }
        for (com.wassup.dinner.notification.domain.RoomTemplate t : com.wassup.dinner.notification.domain.RoomTemplate.values()) {
            if (templateRepository.findByCode(t.name()).isEmpty()) {
                templateRepository.save(new MessageTemplate(t.name(), t.getDefaultTitle(), t.getDefaultBody(), clock.now()));
            }
        }
        if (venueRepository.count() == 0) {
            seedVenues();
        }
        if (slotRepository.count() == 0) {
            // 신청서와 동일하게 매주 목요일 19:30, 4개 지역
            LocalDate first = clock.today().with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
            List<LocalDate> dates = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                dates.add(first.plusWeeks(i));
            }
            slotService.createBulk(new SlotService.BulkSlotRequest(dates, Arrays.asList(Region.values()), LocalTime.of(19, 30), 24));
            log.info("목요일 회차 생성: {}", dates);
        }
    }

    private void seedVenues() {
        venue("모노키친 합정", Region.HONGDAE_HAPJEONG, "서울 마포구 양화로 45", PriceRange.P20_30, "1인 2~3만원 (파스타·리조또)", 2);
        venue("하루식탁 홍대", Region.HONGDAE_HAPJEONG, "서울 마포구 와우산로 21", PriceRange.P10_20, "1인 1~2만원 (한식 정식)", 2);
        venue("오븐앤테이블 잠실", Region.JAMSIL, "서울 송파구 백제고분로 41", PriceRange.P20_30, "1인 2~3만원 (화덕피자)", 2);
        venue("송리단 브런치하우스", Region.JAMSIL, "서울 송파구 오금로 18", PriceRange.P10_20, "1인 1~2만원 (브런치)", 1);
        venue("신논현 그릴바", Region.GANGNAM, "서울 강남구 봉은사로 102", PriceRange.P30_PLUS, "1인 3만원 이상 (스테이크)", 2);
        venue("강남 소반", Region.GANGNAM, "서울 강남구 강남대로 470", PriceRange.P20_30, "1인 2~3만원 (퓨전 한식)", 2);
        venue("을지로 노포식당", Region.EULJIRO_JONGNO, "서울 중구 을지로 157", PriceRange.P10_20, "1인 1~2만원 (노포 한식)", 2);
        venue("종로 다이닝룸", Region.EULJIRO_JONGNO, "서울 종로구 종로 33", PriceRange.P20_30, "1인 2~3만원 (비스트로)", 2);
    }

    private void venue(String name, Region region, String address, PriceRange price, String guide, int tablesPerSlot) {
        String map = "https://map.kakao.com/?q=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
        venueRepository.save(new Venue(name, region, address, map, price, guide, tablesPerSlot, clock.now()));
    }
}
