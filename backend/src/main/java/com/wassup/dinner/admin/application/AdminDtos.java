package com.wassup.dinner.admin.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 운영자 웹 어드민 응답 모델 모음. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record Dashboard(
            Kpi kpi,
            Pending pending,
            List<SlotSummary> slots,
            LocalDateTime now) {
    }

    /** 핵심 지표 (PRD 3장) — 비율은 0~100(%) */
    public record Kpi(
            long totalApplications,
            long paidApplications,
            double paymentConversionRate,
            long confirmedParticipants,
            double matchSuccessRate,
            long confirmedTables,
            double autoConfirmRate,
            long reallocatedAttempts,
            double reallocationSuccessRate,
            double alternativeTransferRate,
            double autoRefundRate,
            double attendanceRate,
            double noShowRate,
            double feedbackResponseRate,
            double revisitRate,
            double operatorInterventionRate) {
    }

    /** 대시보드에서 바로 보여야 하는 처리 대기 건 */
    public record Pending(
            long awaitingResolution,
            long unresolvedConflicts,
            long tablesWithoutVenue,
            long messageFailures,
            long refundFailures,
            long lateCancelOrNoShowCandidates,
            long openReports,
            long openExceptions) {
    }

    public record SlotSummary(Long slotId, String label, String status, LocalDateTime matchAt, long applicants,
                              long paid, long tables, long confirmed, long failed) {
    }

    public record ApplicantRow(Long applicationId, Long userId, String name, String phone, String instagramId,
                               Integer birthYear, String gender, String job, String mbti, List<String> slotLabels,
                               String otherRegion, String product, String appStatus, String paymentStatus,
                               String matchStatus, String attendanceStatus, Long tableId, String tableCode,
                               int reallocationCount, String statusReason, long pastAttended, long noShows,
                               long reportsReceived, LocalDateTime submittedAt) {
    }

    public record MemberDetail(Long applicationId, Long userId, String name, String phone, int age, String gender,
                               String mbti, String job, List<String> interests, List<String> traits,
                               List<String> desiredTypes, List<String> priceRanges, String foodRestrictions,
                               boolean slotSatisfied, boolean desireSatisfied, List<String> metBeforeWith,
                               int reallocationCount, String placementReason, String matchStatus,
                               String attendanceStatus, String confirmNotification) {
    }

    public record TableDetail(Long tableId, String tableCode, Long slotId, String slotLabel, String status,
                              String formation, boolean autoConfirmed, int memberCount, Map<String, Double> scores,
                              int minBirthYear, int maxBirthYear, int ageGap, int male, int female, String mbtiSummary,
                              List<String> commonInterests, int metBeforePairs, boolean exclusionConflict,
                              int reallocatedMembers, String curation, LocalDateTime confirmedAt,
                              VenueSummary venue, Long chatRoomId, long chatMessages,
                              List<PostStatus> scheduledPosts, List<MemberDetail> members, Long matchRunId) {
    }

    public record VenueSummary(Long venueId, String name, String address, String reservationName, String priceRange) {
    }

    public record PostStatus(String kind, LocalDateTime scheduledAt, String status, int attempts, String lastError) {
    }

    public record TableRow(Long tableId, String tableCode, Long slotId, String slotLabel, String status, int memberCount,
                           double totalScore, int ageGap, int male, int female, String venueName, boolean venueMissing,
                           String formation, LocalDateTime confirmedAt) {
    }

    public record MatchRunDetail(Long id, List<String> slotLabels, String trigger, String algorithmVersion,
                                 String policySnapshot, int candidates, int excluded, int initialTables, int splits,
                                 int merges, int reallocated, int dissolved, int confirmedTables,
                                 int confirmedParticipants, int failed, LocalDateTime startedAt,
                                 LocalDateTime finishedAt, List<String> log, List<TableRow> tables,
                                 List<ApplicantRow> failedApplicants) {
    }

    public record AttendanceRow(Long applicationId, Long userId, String name, String phone, String slotLabel,
                                String tableCode, String attendanceStatus, LocalDateTime checkedInAt,
                                LocalDateTime canceledAt, boolean openReview) {
    }

    public record RefundRow(Long refundId, Long applicationId, Long userId, String name, int amount, String status,
                            int attempts, String failureReason, String reason, LocalDateTime requestedAt,
                            LocalDateTime completedAt) {
    }
}
