package com.wassup.dinner.safety.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionType;
import com.wassup.dinner.safety.domain.SafetyReport;
import com.wassup.dinner.safety.domain.SafetyReportRepository;
import com.wassup.dinner.safety.domain.UserBlock;
import com.wassup.dinner.safety.domain.UserBlockRepository;
import com.wassup.dinner.user.domain.AccountStatus;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 신고·차단 (SAF-01, 02). 신고 즉시 두 사람을 향후 매칭에서 분리하고 운영자 검토 목록에 올린다. */
@Service
@RequiredArgsConstructor
public class SafetyService {

    private final SafetyReportRepository reportRepository;
    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ExceptionCaseService exceptionCaseService;
    private final AppClock clock;

    public record ReportRequest(@NotNull Long reportedUserId, Long tableId, @NotBlank String category,
                                String detail, String evidenceUrl) {
    }

    public record ActionRequest(@NotNull SafetyReport.Action action, String note, Integer restrictDays) {
    }

    public record ReportDto(Long id, Long reporterId, String reporterName, Long reportedId, String reportedName,
                            String reportedAccountStatus, Long tableId, String category, String detail, String evidenceUrl,
                            String status, String action, String adminNote, LocalDateTime createdAt, LocalDateTime handledAt) {
    }

    @Transactional
    public Long report(Long reporterId, ReportRequest r) {
        if (reporterId.equals(r.reportedUserId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "본인을 신고할 수 없습니다.");
        }
        userRepository.findById(r.reportedUserId()).orElseThrow(() -> ApiException.notFound("사용자"));
        SafetyReport report = reportRepository.save(new SafetyReport(reporterId, r.reportedUserId(), r.tableId(),
                r.category(), r.detail(), r.evidenceUrl(), clock.now()));
        block(reporterId, r.reportedUserId(), UserBlock.Source.REPORT);
        exceptionCaseService.open(ExceptionType.SAFETY_REPORT, "report:" + report.getId(),
                "[" + r.category() + "] 신고 접수 - 두 사용자는 이후 매칭에서 자동 분리됩니다.",
                new ExceptionCaseService.Refs(null, r.tableId(), null, r.reportedUserId()));
        return report.getId();
    }

    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "본인을 차단할 수 없습니다.");
        }
        block(blockerId, blockedId, UserBlock.Source.MANUAL);
    }

    private void block(Long blockerId, Long blockedId, UserBlock.Source source) {
        if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            blockRepository.save(new UserBlock(blockerId, blockedId, source, clock.now()));
        }
    }

    @Transactional(readOnly = true)
    public List<ReportDto> reports() {
        List<SafetyReport> all = reportRepository.findAllByOrderByIdDesc();
        Map<Long, User> users = userRepository.findAllById(all.stream()
                        .flatMap(r -> Stream.of(r.getReporterId(), r.getReportedId())).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return all.stream().map(r -> toDto(r, users)).toList();
    }

    /** 운영자 조치: 경고 / 기간 참가 제한 / 영구 제한 / 기각 */
    @Transactional
    public ReportDto act(Long reportId, ActionRequest r, String actor) {
        SafetyReport report = reportRepository.findById(reportId).orElseThrow(() -> ApiException.notFound("신고"));
        User reported = userRepository.findById(report.getReportedId()).orElseThrow();
        switch (r.action()) {
            case WARNING -> reported.setStatus(AccountStatus.WARNED);
            case RESTRICTED -> {
                reported.setStatus(AccountStatus.RESTRICTED);
                reported.setRestrictedUntil(r.restrictDays() == null ? null : clock.today().plusDays(r.restrictDays()));
            }
            case BANNED -> reported.setStatus(AccountStatus.BANNED);
            case NONE -> {
            }
        }
        report.setAction(r.action());
        report.setStatus(r.action() == SafetyReport.Action.NONE ? SafetyReport.Status.DISMISSED : SafetyReport.Status.ACTIONED);
        report.setAdminNote(r.note());
        report.setHandledBy(actor);
        report.setHandledAt(clock.now());
        exceptionCaseService.autoResolve(ExceptionType.SAFETY_REPORT, "report:" + reportId, "조치: " + r.action());
        Map<Long, User> users = userRepository.findAllById(List.of(report.getReporterId(), report.getReportedId()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return toDto(report, users);
    }

    private static ReportDto toDto(SafetyReport r, Map<Long, User> users) {
        User reporter = users.get(r.getReporterId());
        User reported = users.get(r.getReportedId());
        return new ReportDto(r.getId(), r.getReporterId(), reporter == null ? null : Objects.toString(reporter.getName(), ""),
                r.getReportedId(), reported == null ? null : Objects.toString(reported.getName(), ""),
                reported == null ? null : reported.getStatus().name(), r.getTableId(), r.getCategory(), r.getDetail(),
                r.getEvidenceUrl(), r.getStatus().name(), r.getAction().name(), r.getAdminNote(), r.getCreatedAt(), r.getHandledAt());
    }
}
