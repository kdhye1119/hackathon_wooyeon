package com.wassup.dinner.user.api;

import com.wassup.dinner.payment.domain.TicketProduct;
import com.wassup.dinner.user.domain.Options;
import com.wassup.dinner.user.domain.Options.Labeled;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Meta", description = "신청서 선택지·정책 문구 (로그인 불필요)")
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    public static final String REFUND_POLICY = """
            - 매칭 및 식당 예약이 완료된 이후에는 단순 변심으로 인한 환불이 어렵습니다.
            - 진행 2일 전부터는 환불이 불가합니다.
            - 당일 취소 및 단체 채팅방 퇴장은 불가합니다.
            - 노쇼 발생 시 이후 우연한 식탁 및 와썹하우스 프로그램 참여가 제한될 수 있습니다.
            - 매칭이 되지 않으면 다른 일정으로 이동, 이용권 보관, 환불 중 하나를 선택할 수 있습니다.""";

    public static final String NOTICE = """
            우연한 식탁은 소개팅이나 이성 만남을 목적으로 한 자리가 아닙니다. 성별 비율을 고려할 수는 있지만, \
            연애 목적의 매칭을 보장하지 않습니다. 서로에게 부담을 주는 행동이나 과도한 연락처 요구는 제한될 수 있습니다.
            신청서를 바탕으로 분위기가 비슷한 분들을 우선 매칭합니다. 모든 신청자가 반드시 매칭되는 것은 아니며, \
            더 잘 맞는 날짜로 안내 드릴 수 있습니다.""";

    public record Option(String code, String label) {
    }

    public record TicketOption(String code, String label, int price, int count, int validDays) {
    }

    public record OptionsResponse(List<Option> genders, List<Option> interests, List<Option> traits,
                                  List<Option> desiredTypes, List<Option> priceRanges, List<Option> regions,
                                  List<String> mbtiTypes, List<TicketOption> tickets,
                                  String refundPolicy, String notice) {
    }

    @Operation(summary = "신청서 선택지 목록 (관심사·성향·지역·가격대·이용권 등)")
    @GetMapping("/options")
    public OptionsResponse options() {
        return new OptionsResponse(
                of(Options.Gender.values()), of(Options.Interest.values()), of(Options.Trait.values()),
                of(Options.DesiredType.values()), of(Options.PriceRange.values()), of(Options.Region.values()),
                Options.MBTI_TYPES,
                Arrays.stream(TicketProduct.values()).filter(TicketProduct::isOnSale)
                        .map(t -> new TicketOption(t.name(), t.getLabel(), t.getPrice(), t.getCount(), t.getValidDays()))
                        .toList(),
                REFUND_POLICY, NOTICE);
    }

    private static List<Option> of(Labeled[] values) {
        return Arrays.stream(values).map(v -> new Option(v.name(), v.getLabel())).toList();
    }
}
