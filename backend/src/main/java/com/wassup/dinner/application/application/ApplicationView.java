package com.wassup.dinner.application.application;

import com.wassup.dinner.common.audit.StatusHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 참가자 앱 홈/상세 카드. 상태값 4종 + 사람이 읽는 상태 문구 + 지금 할 수 있는 행동(nextActions)을 함께 내려
 * 실패·대기 상태에서도 한 화면에서 다음 행동을 고를 수 있게 한다.
 *
 * nextActions: PAY, CANCEL, VIEW_ROOM, CHECK_IN, FEEDBACK, CHOOSE_RESOLUTION, RESPOND_SEAT_OFFER, REAPPLY, VIEW_NEW_APPLICATION
 */
public record ApplicationView(
        Long id,
        String appStatus,
        String paymentStatus,
        String matchStatus,
        String attendanceStatus,
        String statusTitle,
        String statusMessage,
        List<String> nextActions,
        List<SlotItem> slots,
        String otherRegion,
        PaymentInfo payment,
        LocalDateTime matchAnnounceAt,
        LocalDateTime refundDeadline,
        boolean cancelWillRefund,
        int reallocationCount,
        String statusReason,
        TableInfo table,
        ResolutionInfo resolution,
        Long transferredToId,
        Long transferredFromId,
        boolean feedbackSubmitted,
        LocalDateTime submittedAt,
        List<TimelineItem> timeline,
        /* 취소석 제안 (응답 대기 중일 때만) */
        SeatOfferInfo seatOffer) {

    public record SeatOfferInfo(String slotLabel, String tableCode, LocalDateTime expiresAt) {
    }

    public record SlotItem(Long id, String label, String region, LocalDateTime startAt, LocalDateTime matchAt,
                           String slotStatus, boolean assigned) {
    }

    public record PaymentInfo(Long paymentId, String product, String productLabel, int amount, Long ticketId) {
    }

    public record TableInfo(Long tableId, String tableCode, Long roomId, String slotLabel, LocalDateTime startAt,
                            int memberCount, String venueName, String address, String mapUrl, String reservationName,
                            String priceGuide, String curation, boolean canCheckIn,
                            /* 운영자가 올린 식당 사진 (없으면 앱에서 로고 표시) */
                            List<String> photoUrls) {
    }

    public record ResolutionInfo(String reason, List<SlotItem> alternatives, String choice, String status,
                                 Long newApplicationId, Integer refundAmount, String refundStatus) {
    }

    public record TimelineItem(String field, String from, String to, String reason, LocalDateTime at) {
        public static TimelineItem of(StatusHistory h) {
            return new TimelineItem(h.getField(), h.getFromValue(), h.getToValue(), h.getReason(), h.getChangedAt());
        }
    }
}
