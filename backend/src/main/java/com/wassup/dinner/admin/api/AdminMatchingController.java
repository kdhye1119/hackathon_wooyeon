package com.wassup.dinner.admin.api;

import com.wassup.dinner.admin.application.AdminDtos.MatchRunDetail;
import com.wassup.dinner.admin.application.AdminDtos.TableDetail;
import com.wassup.dinner.admin.application.AdminDtos.TableRow;
import com.wassup.dinner.admin.application.AdminQueryService;
import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.chat.application.TableBriefing;
import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.matching.application.MatchingPolicyService;
import com.wassup.dinner.matching.application.MatchingPolicyService.PolicyRequest;
import com.wassup.dinner.matching.application.MatchingService;
import com.wassup.dinner.matching.domain.DiningTable;
import com.wassup.dinner.matching.domain.DiningTableRepository;
import com.wassup.dinner.matching.domain.MatchRun;
import com.wassup.dinner.matching.domain.MatchingPolicy;
import com.wassup.dinner.slot.domain.EventSlot;
import com.wassup.dinner.slot.domain.EventSlotRepository;
import com.wassup.dinner.venue.application.VenueService;
import com.wassup.dinner.venue.domain.Venue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Matching", description = "자동 매칭 모니터/실행 기록/테이블 상세/정책")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMatchingController {

    private final AdminQueryService queryService;
    private final MatchingService matchingService;
    private final MatchingPolicyService policyService;
    private final DiningTableRepository tableRepository;
    private final EventSlotRepository slotRepository;
    private final VenueService venueService;
    private final ChatService chatService;
    private final TableBriefing briefing;
    private final com.wassup.dinner.matching.application.TableService tableService;

    public record RunRequest(@NotNull List<Long> slotIds) {
    }

    public record VenueAssignRequest(@NotNull Long venueId) {
    }

    @Operation(summary = "자동 매칭 실행 기록 목록")
    @GetMapping("/match-runs")
    public List<MatchRunDetail> runs() {
        return queryService.runs();
    }

    @Operation(summary = "매칭 실행 상세 (생성/분리/병합/재배치/확정/실패 로그, 실패자)")
    @GetMapping("/match-runs/{id}")
    public MatchRunDetail run(@PathVariable Long id) {
        return queryService.run(id);
    }

    @Operation(summary = "[데모] 지정 회차 즉시 매칭 (마감 시각 무시). 이미 매칭된 회차는 건너뜀 (멱등)")
    @PostMapping("/match-runs")
    public MatchRunDetail runNow(@RequestBody RunRequest request) {
        return matchingService.run(request.slotIds(), MatchRun.Trigger.ADMIN)
                .map(r -> queryService.run(r.getId()))
                .orElseThrow(() -> new ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_STATE,
                        "모집 중인 회차가 없습니다 (이미 매칭됨)."));
    }

    @Operation(summary = "테이블 목록 (slotId 필터)")
    @GetMapping("/tables")
    public List<TableRow> tables(@RequestParam(required = false) Long slotId) {
        return queryService.tables(slotId);
    }

    @Operation(summary = "테이블 상세: 구성원/조건 충족/점수/식당/단체방/발송 상태")
    @GetMapping("/tables/{id}")
    public TableDetail table(@PathVariable Long id) {
        return queryService.table(id);
    }

    @Operation(summary = "식당 수동 배정 (식당 부족 예외 처리) → 단체방에 장소 안내 자동 게시")
    @PostMapping("/tables/{id}/venue")
    @Transactional
    public TableDetail assignVenue(@PathVariable Long id, @RequestBody VenueAssignRequest request) {
        DiningTable table = tableRepository.findById(id).orElseThrow(() -> ApiException.notFound("테이블"));
        EventSlot slot = slotRepository.findById(table.getSlotId()).orElseThrow();
        Venue venue = venueService.assignManually(table, slot, request.venueId());
        chatService.postNotice(table.getId(), "📍 식당이 확정되었어요!\n\n" + chatService.announcement(table, slot, venue));
        return queryService.table(id);
    }

    public record MoveRequest(@NotNull Long applicationId, Long targetTableId, String reason) {
    }

    public record DissolveRequest(String reason) {
    }

    @Operation(summary = "긴급 수정: 참가자 이동 (targetTableId 생략 시 새 테이블로 분리, 전원 이동 시 병합). 이력 기록")
    @PostMapping("/tables/{id}/move")
    public TableDetail moveMember(LoginUser admin, @PathVariable Long id, @RequestBody MoveRequest request) {
        DiningTable target = tableService.moveMember(request.applicationId(), request.targetTableId(), request.reason(), "ADMIN:" + admin.userId());
        return queryService.table(target.getId());
    }

    @Operation(summary = "긴급 수정: 테이블 해제 (남은 참가자는 자동 재배치, 안 되면 대체 일정 안내)")
    @PostMapping("/tables/{id}/dissolve")
    public void dissolve(LoginUser admin, @PathVariable Long id, @RequestBody(required = false) DissolveRequest request) {
        tableService.dissolveByAdmin(id, request == null ? null : request.reason(), "ADMIN:" + admin.userId());
    }

    @Operation(summary = "매칭 정책 조회 (가중치/최소 점수/필수 조건)")
    @GetMapping("/policy")
    public MatchingPolicy policy() {
        return policyService.get();
    }

    @Operation(summary = "매칭 가중치/최소 점수 수정")
    @PutMapping("/policy")
    public MatchingPolicy updatePolicy(LoginUser admin, @RequestBody PolicyRequest request) {
        return policyService.update(request, admin.actor());
    }
}
