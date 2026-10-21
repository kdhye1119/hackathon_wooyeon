package com.wassup.dinner.feedback.api;

import com.wassup.dinner.common.auth.LoginUser;
import com.wassup.dinner.feedback.application.AttendanceService;
import com.wassup.dinner.feedback.application.FeedbackService;
import com.wassup.dinner.feedback.application.FeedbackService.FeedbackRequest;
import com.wassup.dinner.feedback.application.FeedbackService.Tablemate;
import com.wassup.dinner.feedback.application.RetentionService;
import com.wassup.dinner.feedback.application.ReviewService;
import com.wassup.dinner.feedback.application.RetentionService.HistoryItem;
import com.wassup.dinner.feedback.application.RetentionService.Recommendation;
import com.wassup.dinner.safety.application.SafetyService;
import com.wassup.dinner.safety.application.SafetyService.ReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Participation", description = "체크인·피드백·신고/차단·참가 이력·다음 일정 추천")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParticipationController {

    private final AttendanceService attendanceService;
    private final FeedbackService feedbackService;
    private final SafetyService safetyService;
    private final RetentionService retentionService;
    private final ReviewService reviewService;

    @Operation(summary = "체크인 (행사 시작 1시간 전 ~ 종료 시각)")
    @PostMapping("/applications/{id}/check-in")
    public void checkIn(LoginUser user, @PathVariable Long id) {
        attendanceService.checkIn(user.userId(), id);
    }

    @Operation(summary = "피드백 대상 테이블 메이트 목록")
    @GetMapping("/applications/{id}/tablemates")
    public List<Tablemate> tablemates(LoginUser user, @PathVariable Long id) {
        return feedbackService.tablemates(user.userId(), id);
    }

    @Operation(summary = "만족도·재참여 의향·사람별 선호(비공개) 제출")
    @PostMapping("/applications/{id}/feedback")
    public void feedback(LoginUser user, @PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        feedbackService.submit(user.userId(), id, request);
    }

    @Operation(summary = "신고 (상대에게 비공개, 자동 차단 + 이후 매칭에서 즉시 분리)")
    @PostMapping("/reports")
    public Map<String, Long> report(LoginUser user, @Valid @RequestBody ReportRequest request) {
        return Map.of("reportId", safetyService.report(user.userId(), request));
    }

    @Operation(summary = "차단 (이후 같은 테이블에 배치되지 않음)")
    @PostMapping("/blocks/{targetUserId}")
    public void block(LoginUser user, @PathVariable Long targetUserId) {
        safetyService.block(user.userId(), targetUserId);
    }

    @Operation(summary = "참가 이력 (날짜·지역·피드백 제출 여부)")
    @GetMapping("/me/history")
    public List<HistoryItem> history(LoginUser user) {
        return retentionService.history(user.userId());
    }

    @Operation(summary = "공개 후기 (sort=recommend 좋아요·추천순 / recent 최신순)")
    @GetMapping("/reviews")
    public List<ReviewService.ReviewDto> reviews(LoginUser user, @RequestParam(defaultValue = "recommend") String sort,
                                                 @RequestParam(defaultValue = "10") int limit) {
        return reviewService.list(user.userId(), sort, limit);
    }

    @Operation(summary = "공개 후기 좋아요 토글")
    @PostMapping("/reviews/{id}/like")
    public ReviewService.LikeResult like(LoginUser user, @PathVariable Long id) {
        return reviewService.toggleLike(user.userId(), id);
    }

    @Operation(summary = "다음 일정 추천")
    @GetMapping("/me/recommendations")
    public List<Recommendation> recommendations(LoginUser user) {
        return retentionService.recommend(user.userId());
    }
}
