package com.wassup.dinner.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 참가권 상품 (FAQ 기준 가격).
 * - 1회권: 1회 참여
 * - 멤버십: 기간 안에 횟수 제한 없이 참여 (count = 0 은 무제한). 매칭 실패 시 실패 횟수만큼 기간 연장
 * - TRIPLE: 이전 상품. 기존 결제·이용권 데이터 호환을 위해 남겨 두고 판매 목록에서는 숨긴다
 */
@Getter
@RequiredArgsConstructor
public enum TicketProduct {
    SINGLE("1회권", 1, 9_900, 60, true),
    MEMBERSHIP_1M("멤버십 1개월", 0, 35_000, 30, true),
    MEMBERSHIP_3M("멤버십 3개월", 0, 75_000, 90, true),
    MEMBERSHIP_6M("멤버십 6개월", 0, 120_000, 180, true),
    TRIPLE("3회권", 3, 15_000, 60, false);

    /** 멤버십 매칭 실패 1회당 연장 일수 (주 1회 운영) */
    public static final int MEMBERSHIP_EXTEND_DAYS = 7;

    private final String label;
    private final int count;
    private final int price;
    private final int validDays;
    /** 신청 화면 판매 목록에 노출 */
    private final boolean onSale;

    public boolean isMembership() {
        return count == 0;
    }

    /** 1회 사용분 금액 (부분 환불 기준). 멤버십은 회당 금액이 없어 환불 대상이 아니다 */
    public int unitPrice() {
        return isMembership() ? 0 : price / count;
    }
}
