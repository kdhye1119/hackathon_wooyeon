package com.wassup.dinner.admin.api;

import com.wassup.dinner.admin.application.AdminDtos.AttendanceRow;
import com.wassup.dinner.admin.application.AdminDtos.RefundRow;
import com.wassup.dinner.admin.application.AdminQueryService;
import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.application.AttendanceService;
import com.wassup.dinner.feedback.application.FeedbackService;
import com.wassup.dinner.feedback.application.FeedbackService.FeedbackSummary;
import com.wassup.dinner.notification.application.NotificationService;
import com.wassup.dinner.notification.application.NotificationService.NotificationDto;
import com.wassup.dinner.notification.domain.MessageTemplate;
import com.wassup.dinner.notification.domain.MessageTemplateRepository;
import com.wassup.dinner.notification.domain.NotificationRepository;
import com.wassup.dinner.ops.application.ExceptionCaseService;
import com.wassup.dinner.ops.domain.ExceptionCase;
import com.wassup.dinner.ops.domain.ExceptionCaseRepository;
import com.wassup.dinner.payment.application.PaymentService.PaymentDto;
import com.wassup.dinner.payment.application.RefundService;
import com.wassup.dinner.payment.domain.PaymentRepository;
import com.wassup.dinner.safety.application.SafetyService;
import com.wassup.dinner.safety.application.SafetyService.ActionRequest;
import com.wassup.dinner.safety.application.SafetyService.ReportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Operations", description = "예외 처리함/결제/환불/참석/노쇼/피드백/신고/자동 메시지")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final com.wassup.dinner.feedback.application.ReviewService reviewService;
    private final ExceptionCaseRepository exceptionRepository;
    private final ExceptionCaseService exceptionCaseService;
    private final AdminQueryService queryService;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final AttendanceService attendanceService;
    private final FeedbackService feedbackService;
    private final SafetyService safetyService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final MessageTemplateRepository templateRepository;
    private final AppClock clock;
    private final com.wassup.dinner.chat.domain.ChatMessageRepository chatMessageRepository;
    private final com.wassup.dinner.chat.domain.ScheduledRoomPostRepository roomPostRepository;
    private final com.wassup.dinner.content.ConversationContentService contentService;
    private final com.wassup.dinner.content.TopicItemRepository topicRepository;

    public record CloseRequest(@NotNull ExceptionCase.Status status, String note) {
    }

    public record NoShowReviewRequest(boolean noShow, String note) {
    }

    public record TemplateRequest(String title, String body) {
    }

    // ---- 예외 처리함

    @Operation(summary = "예외 처리함 (status 생략 시 미처리만)")
    @GetMapping("/exceptions")
    public List<ExceptionCase> exceptions(@RequestParam(required = false) ExceptionCase.Status status,
                                          @RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            return exceptionRepository.findAllByOrderByIdDesc();
        }
        return exceptionRepository.findByStatusOrderByIdDesc(status == null ? ExceptionCase.Status.OPEN : status);
    }

    @Operation(summary = "예외 처리 완료/기각")
    @PostMapping("/exceptions/{id}/close")
    public ExceptionCase close(LoginUser admin, @PathVariable Long id, @Valid @RequestBody CloseRequest request) {
        return exceptionCaseService.close(id, request.status(), request.note(), admin.actor());
    }

    // ---- 결제·환불

    @Operation(summary = "결제 내역")
    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public List<PaymentDto> payments() {
        return paymentRepository.findAllByOrderByIdDesc().stream().map(PaymentDto::of).toList();
    }

    @Operation(summary = "자동 환불 내역 (실패 건 포함)")
    @GetMapping("/refunds")
    public List<RefundRow> refunds() {
        return queryService.refunds();
    }

    @Operation(summary = "환불 실패 건 재시도")
    @PostMapping("/refunds/{id}/retry")
    public List<RefundRow> retryRefund(LoginUser admin, @PathVariable Long id) {
        refundService.retry(id, admin.actor());
        return queryService.refunds();
    }

    // ---- 참석

    @Operation(summary = "참석 관리: 체크인/사전 취소/당일 취소/노쇼 후보")
    @GetMapping("/attendance")
    public List<AttendanceRow> attendance(@RequestParam(required = false) Long slotId) {
        return queryService.attendance(slotId);
    }

    @Operation(summary = "노쇼 후보 검토 (noShow=false 면 참석으로 정정)")
    @PostMapping("/attendance/{applicationId}/review")
    public void reviewNoShow(LoginUser admin, @PathVariable Long applicationId, @RequestBody NoShowReviewRequest request) {
        attendanceService.reviewNoShow(applicationId, request.noShow(), request.note(), admin.actor());
    }

    // ---- 피드백·안전

    @Operation(summary = "만족도 요약 (평균/NPS/사람별 선호 수)과 최근 응답")
    @GetMapping("/feedback")
    public FeedbackSummary feedback() {
        return feedbackService.summary();
    }

    public record VisibilityRequest(boolean hidden) {
    }

    @Operation(summary = "공개 후기 노출 관리: 숨김 / 다시 공개")
    @PostMapping("/feedback/{id}/visibility")
    public void visibility(@PathVariable Long id, @RequestBody VisibilityRequest request) {
        reviewService.setHidden(id, request.hidden());
    }

    @Operation(summary = "신고 목록")
    @GetMapping("/reports")
    public List<ReportDto> reports() {
        return safetyService.reports();
    }

    @Operation(summary = "신고 조치: NONE(기각)/WARNING/RESTRICTED(restrictDays)/BANNED")
    @PostMapping("/reports/{id}/action")
    public ReportDto act(LoginUser admin, @PathVariable Long id, @Valid @RequestBody ActionRequest request) {
        return safetyService.act(id, request, admin.actor());
    }

    // ---- 자동 메시지

    @Operation(summary = "자동 발송 이력 (최근 200건)")
    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<NotificationDto> notifications() {
        return notificationRepository.findTop200ByOrderByIdDesc().stream().map(NotificationDto::of).toList();
    }

    @Operation(summary = "발송 실패 알림 재발송")
    @PostMapping("/notifications/{id}/retry")
    public NotificationDto retryNotification(@PathVariable Long id) {
        return notificationService.retry(id);
    }

    /** 단체방 자동 메시지 발송 기록: 게시된 공지·리마인드·대화 주제 + 반복 실패한 예약 게시 */
    public record RoomSendLog(String key, String kind, Long roomId, java.time.LocalDateTime at, String status,
                              Long postId, int attempts) {
    }

    @Operation(summary = "단체방 자동 메시지 발송 기록 (공지/리마인드/대화 주제)")
    @GetMapping("/room-messages")
    @Transactional(readOnly = true)
    public List<RoomSendLog> roomMessages() {
        var types = java.util.List.of(com.wassup.dinner.chat.domain.ChatMessage.Type.NOTICE,
                com.wassup.dinner.chat.domain.ChatMessage.Type.REMINDER, com.wassup.dinner.chat.domain.ChatMessage.Type.CONTENT);
        var out = new java.util.ArrayList<RoomSendLog>();
        chatMessageRepository.findByTypeInOrderByIdDesc(types).forEach(m ->
                out.add(new RoomSendLog("m" + m.getId(), m.getType().name(), m.getRoomId(), m.getCreatedAt(), "SENT", null, 1)));
        roomPostRepository.findByStatus(com.wassup.dinner.chat.domain.ScheduledRoomPost.Status.FAILED).forEach(p ->
                out.add(new RoomSendLog("p" + p.getId(), p.getKind().name(), p.getRoomId(), p.getScheduledAt(), "FAILED", p.getId(), p.getAttempts())));
        return out;
    }

    @Operation(summary = "실패한 단체방 예약 게시 다시 보내기 (다음 자동화 실행 때 발송)")
    @PostMapping("/room-posts/{id}/retry")
    @Transactional
    public void retryRoomPost(@PathVariable Long id) {
        var p = roomPostRepository.findById(id).orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("단체방 메시지"));
        p.setStatus(com.wassup.dinner.chat.domain.ScheduledRoomPost.Status.PENDING);
        p.setAttempts(0);
        p.setScheduledAt(clock.now());
    }

    @Operation(summary = "메시지 템플릿 목록 ({변수} 치환)")
    @GetMapping("/templates")
    public List<MessageTemplate> templates() {
        return templateRepository.findAll();
    }

    @Operation(summary = "메시지 템플릿 수정")
    @PutMapping("/templates/{code}")
    @Transactional
    public MessageTemplate updateTemplate(@PathVariable String code, @RequestBody TemplateRequest request) {
        MessageTemplate t = templateRepository.findByCode(code)
                .orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("템플릿"));
        if (request.title() != null) t.setTitle(request.title());
        if (request.body() != null) t.setBody(request.body());
        t.setUpdatedAt(clock.now());
        return t;
    }

    @Operation(summary = "메시지 템플릿 기본 문구로 되돌리기")
    @PostMapping("/templates/{code}/reset")
    @Transactional
    public MessageTemplate resetTemplate(@PathVariable String code) {
        MessageTemplate t = templateRepository.findByCode(code)
                .orElseThrow(() -> com.wassup.dinner.common.error.ApiException.notFound("템플릿"));
        String[] d = defaults(code);
        t.setTitle(d[0]);
        t.setBody(d[1]);
        t.setUpdatedAt(clock.now());
        return t;
    }

    private static String[] defaults(String code) {
        for (var t : com.wassup.dinner.notification.domain.RoomTemplate.values()) {
            if (t.name().equals(code)) return new String[]{t.getDefaultTitle(), t.getDefaultBody()};
        }
        var n = com.wassup.dinner.notification.domain.NotificationType.valueOf(code);
        return new String[]{n.getDefaultTitle(), n.getDefaultBody()};
    }

    // ---- 대화 주제 라이브러리

    public record TopicRequest(@NotNull String category, @NotNull String text) {
    }

    public record TopicDto(Long id, String category, String text) {
    }

    public record TopicLibrary(java.util.Map<String, Integer> builtIn, List<TopicDto> custom) {
    }

    @Operation(summary = "대화 주제 라이브러리 (기본 제공 수 + 운영자 등록 주제)")
    @GetMapping("/topics")
    public TopicLibrary topics() {
        return new TopicLibrary(contentService.builtInCounts(), topicRepository.findAllByOrderByIdDesc().stream()
                .map(t -> new TopicDto(t.getId(), t.getCategory(), t.getText())).toList());
    }

    @Operation(summary = "대화 주제 등록 (category = 관심사 코드, GENERAL, ICEBREAKER). 다음 단체방 대화 주제부터 반영")
    @PostMapping("/topics")
    public TopicDto addTopic(@Valid @RequestBody TopicRequest r) {
        String text = r.text().trim();
        if (text.isEmpty() || text.length() > 300) {
            throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_REQUEST, "주제는 1~300자로 입력해 주세요.");
        }
        if (!contentService.builtInCounts().containsKey(r.category())) {
            throw new com.wassup.dinner.common.error.ApiException(com.wassup.dinner.common.error.ErrorCode.INVALID_REQUEST, "알 수 없는 카테고리예요.");
        }
        var t = topicRepository.save(new com.wassup.dinner.content.TopicItem(r.category(), text, clock.now()));
        return new TopicDto(t.getId(), t.getCategory(), t.getText());
    }

    @Operation(summary = "등록한 대화 주제 삭제")
    @DeleteMapping("/topics/{id}")
    public void deleteTopic(@PathVariable Long id) {
        topicRepository.deleteById(id);
    }
}
