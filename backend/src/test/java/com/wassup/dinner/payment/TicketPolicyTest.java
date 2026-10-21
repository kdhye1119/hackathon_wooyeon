package com.wassup.dinner.payment;

import com.wassup.dinner.payment.domain.Ticket;
import com.wassup.dinner.payment.domain.TicketProduct;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.user.domain.Options.Region;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** FAQ 기준 이용권·환불 정책 */
class TicketPolicyTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Test
    void 가격은_FAQ_기준() {
        assertThat(TicketProduct.SINGLE.getPrice()).isEqualTo(9_900);
        assertThat(TicketProduct.MEMBERSHIP_1M.getPrice()).isEqualTo(35_000);
        assertThat(TicketProduct.MEMBERSHIP_3M.getPrice()).isEqualTo(75_000);
        assertThat(TicketProduct.MEMBERSHIP_6M.getPrice()).isEqualTo(120_000);
        assertThat(TicketProduct.TRIPLE.isOnSale()).isFalse();
    }

    @Test
    void 멤버십은_기간_안에_횟수_제한_없이_사용() {
        Ticket t = new Ticket(1L, TicketProduct.MEMBERSHIP_1M, 1L, now);
        for (int i = 0; i < 10; i++) {
            t.use(now);
        }
        assertThat(t.isUsable(now)).isTrue();
        assertThat(t.isUsable(now.plusDays(31))).isFalse();
    }

    @Test
    void 멤버십은_매칭_실패마다_기한_연장() {
        Ticket t = new Ticket(1L, TicketProduct.MEMBERSHIP_1M, 1L, now);
        LocalDateTime before = t.getExpiresAt();
        t.extend(TicketProduct.MEMBERSHIP_EXTEND_DAYS);
        t.extend(TicketProduct.MEMBERSHIP_EXTEND_DAYS);
        assertThat(t.getExpiresAt()).isEqualTo(before.plusDays(14));
    }

    @Test
    void 일회권은_사용하면_소진되고_보관하면_복구() {
        Ticket t = new Ticket(1L, TicketProduct.SINGLE, 1L, now);
        t.use(now);
        assertThat(t.isUsable(now)).isFalse();
        t.restore();
        assertThat(t.isUsable(now)).isTrue();
        assertThat(TicketProduct.SINGLE.unitPrice()).isEqualTo(9_900);
    }

    @Test
    void 목요일_행사는_화요일_24시부터_환불_불가() {
        LocalDate thursday = LocalDate.of(2026, 10, 29);
        EventSlot slot = new EventSlot(thursday, LocalTime.of(19, 30), Region.HONGDAE_HAPJEONG, 24,
                thursday.minusDays(1).atStartOfDay(), thursday.minusDays(1).atStartOfDay(), now);
        assertThat(slot.refundDeadline()).isEqualTo(LocalDateTime.of(2026, 10, 28, 0, 0));
    }
}
