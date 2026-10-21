package com.wassup.dinner.ops.application;

import com.wassup.dinner.application.application.ApplicationService;
import com.wassup.dinner.application.application.ApplicationService.SubmitRequest;
import com.wassup.dinner.application.application.ApplicationService.SubmitResponse;
import com.wassup.dinner.common.auth.Role;
import com.wassup.dinner.common.auth.TokenProvider;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.payment.application.PaymentService;
import com.wassup.dinner.payment.domain.TicketProduct;
import com.wassup.dinner.slot.application.SlotService;
import com.wassup.dinner.slot.application.SlotService.SlotDto;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.user.domain.Options.*;
import com.wassup.dinner.user.domain.Profile;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** 해커톤 시연용 테스트 참가자·시나리오 생성. 실제 신청·결제 서비스 흐름을 그대로 통과한다. */
@Service
@RequiredArgsConstructor
public class DemoService {

    private static final String[] LAST = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신"};
    private static final String[] FIRST = {"민준", "서연", "도윤", "지우", "하준", "서윤", "시우", "하은", "지호", "수아",
            "준서", "지민", "유진", "채원", "현우", "다은", "건우", "예린", "우진", "소율", "선우", "윤서", "연우", "가은"};
    private static final String[] JOBS = {"마케터", "개발자", "간호사", "디자이너", "기획자", "회계사", "교사", "연구원",
            "PD", "영업", "약사", "대학원생", "공무원", "바리스타"};

    private final UserRepository userRepository;
    private final ApplicationService applicationService;
    private final PaymentService paymentService;
    private final SlotService slotService;
    private final EventSlotRepository slotRepository;
    private final TokenProvider tokenProvider;
    private final AppClock clock;
    private final com.wassup.dinner.payment.domain.TicketRepository ticketRepository;

    public record SeedRequest(List<LocalDate> dates, List<Region> regions, int count, Integer minBirthYear,
                              Integer maxBirthYear, boolean pay, TicketProduct product, Long randomSeed) {
    }

    public record DemoUser(Long userId, String phone, String name, Integer birthYear, String gender, String mbti,
                           Long applicationId, String paymentStatus, String token) {
    }

    public record ScenarioResponse(List<SlotDto> slots, List<DemoUser> participants, String guide) {
    }

    /** 지정 날짜·지역에 테스트 참가자 N명을 신청·결제까지 진행 */
    @Transactional
    public List<DemoUser> seed(SeedRequest r) {
        Random random = new Random(r.randomSeed() == null ? System.nanoTime() : r.randomSeed());
        int minYear = r.minBirthYear() == null ? 1993 : r.minBirthYear();
        int maxYear = r.maxBirthYear() == null ? 2000 : r.maxBirthYear();
        List<DemoUser> result = new ArrayList<>();
        for (int i = 0; i < r.count(); i++) {
            User u = createUser(random, minYear + random.nextInt(maxYear - minYear + 1), i % 2 == 0 ? Gender.MALE : Gender.FEMALE);
            result.add(apply(u, r.dates(), r.regions(), r.pay(), r.product() == null ? TicketProduct.SINGLE : r.product()));
        }
        return result;
    }

    /**
     * 시연 시나리오 한 번에 만들기 (3일 뒤 이후 비어 있는 날짜에 회차 생성):
     * - 홍대/합정 회차: 1990년대 중반생 14명 → 테이블 자동 분리 (6+4+4 등)
     * - 잠실 회차: 3명만 1지망 → 4명 미만 → 홍대/합정 2지망으로 자동 재배치
     * - 2명은 나이 차이 8살 초과 → 재배치 실패 → 대체 일정 안내
     */
    @Transactional
    public ScenarioResponse scenario(long randomSeed) {
        // 아직 회차가 없는 날짜를 골라 매번 새 시나리오를 만든다
        LocalDate date = clock.today().plusDays(3);
        while (!slotRepository.findByDateInAndRegionIn(List.of(date, date.plusDays(7)),
                List.of(Region.HONGDAE_HAPJEONG, Region.JAMSIL)).isEmpty()) {
            date = date.plusDays(1);
        }
        List<SlotDto> slots = slotService.createBulk(new SlotService.BulkSlotRequest(
                List.of(date), List.of(Region.HONGDAE_HAPJEONG, Region.JAMSIL), LocalTime.of(19, 30), 24));
        // 다음 주 같은 지역 대체 일정
        slots = new ArrayList<>(slots);
        slots.addAll(slotService.createBulk(new SlotService.BulkSlotRequest(
                List.of(date.plusDays(7)), List.of(Region.HONGDAE_HAPJEONG), LocalTime.of(19, 30), 24)));
        Random random = new Random(randomSeed);
        List<DemoUser> users = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            users.add(apply(createUser(random, 1993 + random.nextInt(6), i % 2 == 0 ? Gender.MALE : Gender.FEMALE),
                    List.of(date), List.of(Region.HONGDAE_HAPJEONG), true, TicketProduct.SINGLE));
        }
        for (int i = 0; i < 3; i++) {
            users.add(apply(createUser(random, 1994 + random.nextInt(4), i % 2 == 0 ? Gender.FEMALE : Gender.MALE),
                    List.of(date), List.of(Region.JAMSIL, Region.HONGDAE_HAPJEONG), true, TicketProduct.MEMBERSHIP_1M));
        }
        users.add(apply(createUser(random, 1982, Gender.MALE), List.of(date), List.of(Region.JAMSIL), true, TicketProduct.SINGLE));
        users.add(apply(createUser(random, 2005, Gender.FEMALE), List.of(date), List.of(Region.JAMSIL), true, TicketProduct.SINGLE));
        String guide = """
                1) POST /api/admin/match-runs {"slotIds":[%d,%d]} 로 즉시 자동 매칭 (또는 /api/admin/demo/clock 으로 매칭 시각까지 이동 후 /tick)
                2) GET /api/admin/match-runs/{id} 로 분리/재배치/실패 로그 확인
                3) participants[].token 으로 참가자 화면 확인: GET /api/applications, GET /api/chat/rooms
                4) /api/admin/demo/clock {"plusDays":2} + /tick → 리마인드, 행사 시각 → 대화 콘텐츠, 종료 후 → 노쇼/피드백 요청
                """.formatted(slots.get(0).id(), slots.get(1).id());
        return new ScenarioResponse(slots, users, guide);
    }

    /** 참가자 본인 기준 시연 시나리오 종류 */
    public enum UserScenario {
        /** 비슷한 나이대 5명과 함께 → 매칭 확정 */
        SUCCESS,
        /** 나 포함 3명뿐 → 최소 인원 미달로 매칭 실패, 대체 일정·보관·환불 선택 */
        FAIL_FEW,
        /** 같은 회차 5명이 모두 15살 이상 차이 → 나만 매칭 실패 */
        FAIL_AGE,
        /** 멤버십으로 신청했는데 인원 미달 → 멤버십 연장 안내 (환불 없음) */
        FAIL_MEMBERSHIP,
        /** 1지망 지역은 2명뿐, 2지망 지역에 4명 → 2지망으로 자동 재배치 */
        REALLOCATE,
        /** 신청만 하고 결제 전 → 결제 대기 화면 */
        PAYMENT_PENDING
    }

    public record UserScenarioResponse(UserScenario type, LocalDate date, java.time.LocalDateTime matchAt, List<SlotDto> slots,
                                       Long applicationId, int others, String guide) {
    }

    /**
     * 로그인한 참가자를 새 회차에 신청시키고, 결과가 원하는 시나리오대로 나오도록 다른 신청자를 채운다.
     * 매번 아직 회차가 없는 날짜에 새 회차를 만들기 때문에 기존 데이터와 섞이지 않는다.
     */
    @Transactional
    public UserScenarioResponse userScenario(Long userId, UserScenario type) {
        User me = userRepository.findById(userId).orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("사용자"));
        if (!me.canApply(clock.today())) {
            // 시연 중 노쇼로 제한된 계정도 다음 시나리오를 볼 수 있게 풀어 준다
            me.setStatus(com.wassup.dinner.user.domain.AccountStatus.ACTIVE);
            me.setRestrictedUntil(null);
        }
        List<Region> regions = type == UserScenario.REALLOCATE
                ? List.of(Region.EULJIRO_JONGNO, Region.GANGNAM) : List.of(Region.GANGNAM);
        // 신청 마감(D-1 00:00)이 아직 남은 날짜 중 해당 지역 회차가 없는 첫 날
        LocalDate date = clock.today().plusDays(2);
        while (!slotRepository.findByDateInAndRegionIn(List.of(date), regions).isEmpty()) {
            date = date.plusDays(1);
        }
        List<SlotDto> slots = slotService.createBulk(new SlotService.BulkSlotRequest(List.of(date), regions, LocalTime.of(19, 30), 24));

        Random random = new Random();
        int myYear = me.getProfile().getBirthYear() == null ? 1996 : me.getProfile().getBirthYear();
        Gender myGender = me.getProfile().getGender() == null ? Gender.FEMALE : me.getProfile().getGender();
        Gender other = myGender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        int others = 0;
        switch (type) {
            case SUCCESS, PAYMENT_PENDING -> {
                for (int i = 0; i < 5; i++) {
                    apply(createUser(random, myYear - 2 + random.nextInt(5), i % 2 == 0 ? other : myGender), List.of(date), regions, true, TicketProduct.SINGLE);
                }
                others = 5;
            }
            case FAIL_FEW, FAIL_MEMBERSHIP -> {
                for (int i = 0; i < 2; i++) {
                    apply(createUser(random, myYear - 1 + random.nextInt(3), i % 2 == 0 ? other : myGender), List.of(date), regions, true, TicketProduct.SINGLE);
                }
                others = 2;
            }
            case FAIL_AGE -> {
                // 나보다 15~18살 위(또는 아래) 사람들끼리만 한 테이블이 된다
                int base = myYear - 15 >= 1975 ? myYear - 18 : myYear + 15;
                for (int i = 0; i < 5; i++) {
                    apply(createUser(random, base + random.nextInt(4), i % 2 == 0 ? other : myGender), List.of(date), regions, true, TicketProduct.SINGLE);
                }
                others = 5;
            }
            case REALLOCATE -> {
                // 1지망(을지로/종로)은 나 포함 2명 → 미달, 2지망(강남/신논현)은 4명이 기다리는 중
                apply(createUser(random, myYear - 1 + random.nextInt(3), other), List.of(date), List.of(Region.EULJIRO_JONGNO), true, TicketProduct.SINGLE);
                for (int i = 0; i < 4; i++) {
                    apply(createUser(random, myYear - 2 + random.nextInt(5), i % 2 == 0 ? myGender : other), List.of(date), List.of(Region.GANGNAM), true, TicketProduct.SINGLE);
                }
                others = 5;
            }
        }

        // 나의 신청: 멤버십 시나리오는 보유 멤버십(없으면 1개월 멤버십 결제), 나머지는 1회권
        Long ticketId = null;
        TicketProduct product = TicketProduct.SINGLE;
        if (type == UserScenario.FAIL_MEMBERSHIP) {
            java.time.LocalDateTime now = clock.now();
            ticketId = ticketRepository.findByUserIdOrderByIdDesc(userId).stream()
                    .filter(t -> t.isMembership() && t.isUsable(now)).map(com.wassup.dinner.payment.domain.Ticket::getId)
                    .findFirst().orElse(null);
            product = ticketId == null ? TicketProduct.MEMBERSHIP_1M : null;
        }
        SubmitResponse res = applicationService.submit(userId, new SubmitRequest(List.of(date), regions, null, product, ticketId));
        if (type != UserScenario.PAYMENT_PENDING && res.paymentRequired()) {
            paymentService.confirm(userId, res.paymentId(), false);
        }
        String guide = switch (type) {
            case SUCCESS -> "매칭 시각으로 이동하면 6명 테이블이 확정되고 단체방이 열려요.";
            case FAIL_FEW -> "매칭 시각으로 이동하면 최소 인원 미달로 실패해요. 일정 이동/보관/환불을 고를 수 있어요.";
            case FAIL_AGE -> "다른 신청자와 나이 차이가 커서 나만 테이블에 들어가지 못해요.";
            case FAIL_MEMBERSHIP -> "멤버십은 환불 대신 기간이 연장되고, 다음 일정으로 옮기거나 그대로 유지해요.";
            case REALLOCATE -> "1지망 지역 인원이 모자라 2지망 지역 테이블로 자동 재배치돼요.";
            case PAYMENT_PENDING -> "결제 대기 상태예요. 마감 전에 결제하지 않으면 신청이 취소돼요.";
        };
        return new UserScenarioResponse(type, date, slots.get(0).matchAt(), slots, res.application().id(), others, guide);
    }

    private User createUser(Random random, int birthYear, Gender gender) {
        String phone;
        do {
            phone = "0109" + String.format("%07d", random.nextInt(10_000_000));
        } while (userRepository.findByPhone(phone).isPresent());
        User u = userRepository.save(new User(phone, LAST[random.nextInt(LAST.length)] + FIRST[random.nextInt(FIRST.length)], clock.now()));
        Profile p = u.getProfile();
        p.setBirthYear(birthYear);
        p.setGender(gender);
        p.setJob(JOBS[random.nextInt(JOBS.length)]);
        p.setInstagramId("demo_" + u.getId());
        p.setMbti("" + (random.nextBoolean() ? 'E' : 'I') + (random.nextInt(3) > 0 ? 'N' : 'S')
                + (random.nextBoolean() ? 'F' : 'T') + (random.nextBoolean() ? 'P' : 'J'));
        p.setInterests(pick(random, Interest.values(), 2, 4));
        p.setTraits(pick(random, Trait.values(), 1, 3));
        p.setDesiredTypes(pick(random, DesiredType.values(), 1, 2));
        p.setPriceRanges(pick(random, PriceRange.values(), 1, 2));
        u.setPrivacyAgreedAt(clock.now());
        u.setPolicyAgreedAt(clock.now());
        return u;
    }

    private DemoUser apply(User u, List<LocalDate> dates, List<Region> regions, boolean pay, TicketProduct product) {
        SubmitResponse res = applicationService.submit(u.getId(), new SubmitRequest(dates, regions, null, product, null));
        String status = res.application().paymentStatus();
        if (pay && res.paymentRequired()) {
            status = paymentService.confirm(u.getId(), res.paymentId(), false).status();
        }
        return new DemoUser(u.getId(), u.getPhone(), u.getName(), u.getProfile().getBirthYear(), u.getProfile().getGender().name(),
                u.getProfile().getMbti(), res.application().id(), status, tokenProvider.issue(u.getId(), Role.USER));
    }

    private static <E extends Enum<E>> Set<String> pick(Random random, E[] values, int min, int max) {
        List<E> list = new ArrayList<>(Arrays.asList(values));
        Collections.shuffle(list, random);
        int n = min + random.nextInt(max - min + 1);
        Set<String> out = new LinkedHashSet<>();
        list.stream().limit(n).forEach(e -> out.add(e.name()));
        return out;
    }
}
