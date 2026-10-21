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

@Tag(name = "Admin - Dashboard & Applicants", description = "운영 대시보드·신청자 목록·CSV")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminQueryService queryService;
    private final AuditService auditService;

    @Operation(summary = "운영 대시보드: 핵심 지표 + 처리 대기 건 + 회차별 현황")
    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return queryService.dashboard();
    }

    @Operation(summary = "신청자 목록 (프로필·희망 일정·결제·매칭·참석·이전 참석/신고 이력) 필터")
    @GetMapping("/applications")
    public List<ApplicantRow> applicants(@RequestParam(required = false) Long slotId,
                                         @RequestParam(required = false) MatchStatus matchStatus,
                                         @RequestParam(required = false) PaymentStatus paymentStatus,
                                         @RequestParam(required = false) String q) {
        return queryService.applicants(slotId, matchStatus, paymentStatus, q);
    }

    @Operation(summary = "신청 상태 변경 이력 (시간·변경 주체·사유)")
    @GetMapping("/applications/{id}/history")
    public List<StatusHistory> history(@PathVariable Long id) {
        return auditService.historyOf("Application", id);
    }

    @Operation(summary = "참가자·테이블 결과 CSV 내보내기 (slotId 생략 시 전체)")
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

    private static String csv(Object... values) {
        return java.util.Arrays.stream(values).map(v -> {
            String s = v == null ? "" : v.toString();
            return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
        }).collect(Collectors.joining(","));
    }
}
