package com.wassup.dinner.admin.api;

import com.wassup.dinner.admin.application.AdminDtos.*;
import com.wassup.dinner.admin.application.AdminQueryService;
import com.wassup.dinner.application.domain.MatchStatus;
import com.wassup.dinner.application.domain.PaymentStatus;
import com.wassup.dinner.common.audit.AuditService;
import com.wassup.dinner.common.audit.StatusHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Admin - Dashboard & Applicants", description = "운영 대시보드/신청자 목록/CSV")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final com.wassup.dinner.user.domain.AdminAccountRepository adminAccountRepository;
    private final AdminQueryService queryService;
    private final AuditService auditService;

    @Operation(summary = "운영 대시보드: 핵심 지표 + 처리 대기 건 + 회차별 현황")
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return queryService.dashboard();
    }

    @Operation(summary = "신청자 목록 (프로필/희망 일정/결제/매칭/참석/이전 참석/신고 이력) 필터")
    @GetMapping("/applications")
    public List<ApplicantRow> applicants(@RequestParam(required = false) Long slotId,
                                         @RequestParam(required = false) MatchStatus matchStatus,
                                         @RequestParam(required = false) PaymentStatus paymentStatus,
                                         @RequestParam(required = false) String q) {
        return queryService.applicants(slotId, matchStatus, paymentStatus, q);
    }

    public record AdminMe(Long id, String loginId, String name, String role) {
    }

    @Operation(summary = "로그인한 운영자와 권한 (OWNER 전체 / OPERATOR 정책 변경 제외 / VIEWER 조회만)")
    @GetMapping("/me")
    public AdminMe me(com.wassup.dinner.common.auth.LoginUser admin) {
        var a = adminAccountRepository.findById(admin.userId()).orElseThrow();
        return new AdminMe(a.getId(), a.getLoginId(), a.getName(), a.effectiveRole().name());
    }

    @Operation(summary = "신청 상태 변경 이력 (시간/변경 주체/사유)")
    @GetMapping("/applications/{id}/history")
    public List<StatusHistory> history(@PathVariable Long id) {
        return auditService.historyOf("Application", id);
    }

    @Operation(summary = "참가자/테이블 결과 CSV 내보내기 (slotId 생략 시 전체)")
    @GetMapping(value = "/applications/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long slotId) {
        List<ApplicantRow> rows = queryService.applicants(slotId, null, null, null);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("신청ID,사용자ID,이름,연락처,인스타그램,출생연도,성별,직업,MBTI,희망회차,기타지역,참가권,신청상태,결제상태,매칭상태,참석상태,테이블ID,테이블,재배치횟수,사유,제출시각\n");
        for (ApplicantRow r : rows) {
            sb.append(csv(r.applicationId(), r.userId(), r.name(), r.phone(), r.instagramId(), r.birthYear(), r.gender(),
                    r.job(), r.mbti(), String.join(" / ", r.slotLabels()), r.otherRegion(), r.product(), r.appStatus(),
                    r.paymentStatus(), r.matchStatus(), r.attendanceStatus(), r.tableId(), r.tableCode(),
                    r.reallocationCount(), r.statusReason(), r.submittedAt())).append('\n');
        }
        String filename = "applications" + (slotId == null ? "" : "-slot" + slotId) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "테이블 결과 CSV 내보내기: 테이블 구성원 한 줄씩 (slotId 생략 시 전체)")
    @GetMapping(value = "/tables/export.csv")
    public ResponseEntity<byte[]> exportTables(@RequestParam(required = false) Long slotId) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("테이블ID,테이블,회차,상태,인원,총점,나이차,남,여,식당,예약명,확정시각,신청ID,이름,나이,성별,MBTI,직업,희망회차충족,원하는상대충족,이전만남,재배치횟수,배치사유,참석상태\n");
        for (var t : queryService.tables(slotId)) {
            var d = queryService.table(t.tableId());
            Double total = d.scores() == null ? null : d.scores().get("total");
            for (var m : d.members()) {
                sb.append(csv(d.tableId(), d.tableCode(), d.slotLabel(), d.status(), d.memberCount(), total == null ? t.totalScore() : total,
                        d.ageGap(), d.male(), d.female(), d.venue() == null ? "" : d.venue().name(), d.venue() == null ? "" : d.venue().reservationName(),
                        d.confirmedAt(), m.applicationId(), m.name(), m.age(), m.gender(), m.mbti(), m.job(),
                        m.slotSatisfied() ? "Y" : "N", m.desireSatisfied() ? "Y" : "N", String.join(" / ", m.metBeforeWith()),
                        m.reallocationCount(), m.placementReason(), m.attendanceStatus())).append('\n');
            }
        }
        String filename = "tables" + (slotId == null ? "" : "-slot" + slotId) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(Object... values) {
        return java.util.Arrays.stream(values).map(v -> {
            String s = v == null ? "" : v.toString();
            return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
        }).collect(Collectors.joining(","));
    }
}
