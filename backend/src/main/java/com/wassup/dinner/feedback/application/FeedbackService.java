package com.wassup.dinner.feedback.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.application.domain.AttendanceStatus;
import com.wassup.dinner.chat.application.ChatService;
import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.domain.Feedback;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.feedback.domain.PersonPreference;
import com.wassup.dinner.feedback.domain.PersonPreferenceRepository;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final PersonPreferenceRepository preferenceRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AppClock clock;
    private final com.wassup.dinner.feedback.domain.ReviewLikeRepository reviewLikeRepository;

    public record FeedbackRequest(
            @Min(1) @Max(5) int tableRating,
            @Min(1) @Max(5) int conversationRating,
            @Min(1) @Max(5) int venueRating,
            @Min(1) @Max(5) int rejoinIntent,
            @Min(0) @Max(10) int recommendScore,
            String comment,
            List<Long> wantAgainUserIds,
            List<Long> avoidUserIds,
            /* 후기로 공개 (홈 화면에 이름 없이 노출) */
            Boolean publicReview) {
    }

    /** 피드백 화면에서 고를 수 있는 같은 테이블 사람들 (제한된 정보) */
    public record Tablemate(Long userId, String displayName, String mbti) {
    }

    @Transactional(readOnly = true)
    public List<Tablemate> tablemates(Long userId, Long applicationId) {
        Application a = owned(userId, applicationId);
        Set<Long> ids = mateIds(a);
        return userRepository.findAllById(ids).stream()
                .map(u -> new Tablemate(u.getId(), ChatService.displayName(u.getName()), u.getProfile().getMbti()))
                .toList();
    }

    /**
     * 만족도·재참여 의향과 사람별 선호(비공개)를 저장한다.
     * AVOID 는 다음 매칭부터 같은 테이블 배치를 금지하고, WANT_AGAIN 은 재회 감점 완화 + 선호 성향 학습에 쓰인다.
     */
    @Transactional
    public void submit(Long userId, Long applicationId, FeedbackRequest r) {
        Application a = owned(userId, applicationId);
        if (a.getTableId() == null || a.getAttendanceStatus() != AttendanceStatus.ATTENDED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "참석(체크인)한 식사에 대해서만 피드백을 남길 수 있어요.");
        }
        if (feedbackRepository.existsByApplicationId(applicationId)) {
            throw new ApiException(ErrorCode.ALREADY_SUBMITTED);
        }
        Set<Long> mates = mateIds(a);
        List<Long> want = r.wantAgainUserIds() == null ? List.of() : r.wantAgainUserIds();
        List<Long> avoid = r.avoidUserIds() == null ? List.of() : r.avoidUserIds();
        if (!mates.containsAll(want) || !mates.containsAll(avoid)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "같은 테이블 참가자만 선택할 수 있어요.");
        }
        if (want.stream().anyMatch(avoid::contains)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "같은 사람을 동시에 선택할 수 없어요.");
        }
        Feedback f = new Feedback(applicationId, userId, a.getTableId(), clock.now());
        f.setTableRating(r.tableRating());
        f.setConversationRating(r.conversationRating());
        f.setVenueRating(r.venueRating());
        f.setRejoinIntent(r.rejoinIntent());
        f.setRecommendScore(r.recommendScore());
        f.setComment(r.comment());
        f.setPublicReview(Boolean.TRUE.equals(r.publicReview()));
        feedbackRepository.save(f);
        new LinkedHashSet<>(want).forEach(to -> preferenceRepository.save(
                new PersonPreference(userId, to, a.getTableId(), PersonPreference.Type.WANT_AGAIN, clock.now())));
        new LinkedHashSet<>(avoid).forEach(to -> preferenceRepository.save(
                new PersonPreference(userId, to, a.getTableId(), PersonPreference.Type.AVOID, clock.now())));
    }

    private Set<Long> mateIds(Application a) {
        if (a.getTableId() == null) {
            return Set.of();
        }
        return applicationRepository.findByTableId(a.getTableId()).stream()
                .filter(x -> !x.getUserId().equals(a.getUserId()))
                .filter(x -> x.getAttendanceStatus() != null)
                .map(Application::getUserId).collect(Collectors.toSet());
    }

    private Application owned(Long userId, Long applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("신청"));
    }

    // ------------------------------------------------------------------ 운영자 요약

    public record FeedbackSummary(long responses, double avgTable, double avgConversation, double avgVenue,
                                  double avgRejoin, double nps, long wantAgainCount, long avoidCount,
                                  List<FeedbackRow> recent) {
    }

    public record FeedbackRow(Long id, Long applicationId, Long userId, String userName, Long tableId, int tableRating,
                              int conversationRating, int venueRating, int rejoinIntent, int recommendScore, String comment,
                              boolean publicReview, boolean hidden, long likes) {
    }

    @Transactional(readOnly = true)
    public FeedbackSummary summary() {
        List<Feedback> all = feedbackRepository.findAllByOrderByIdDesc();
        Map<Long, String> names = userRepository.findAllById(all.stream().map(Feedback::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> Objects.toString(u.getName(), "")));
        long promoters = all.stream().filter(f -> f.getRecommendScore() >= 9).count();
        long detractors = all.stream().filter(f -> f.getRecommendScore() <= 6).count();
        double nps = all.isEmpty() ? 0 : Math.round(1000.0 * (promoters - detractors) / all.size()) / 10.0;
        Map<Long, Long> likes = reviewLikeRepository.findByFeedbackIdIn(all.stream().map(Feedback::getId).toList()).stream()
                .collect(Collectors.groupingBy(com.wassup.dinner.feedback.domain.ReviewLike::getFeedbackId, Collectors.counting()));
        return new FeedbackSummary(all.size(),
                avg(all, Feedback::getTableRating), avg(all, Feedback::getConversationRating), avg(all, Feedback::getVenueRating),
                avg(all, Feedback::getRejoinIntent), nps,
                preferenceRepository.findByType(PersonPreference.Type.WANT_AGAIN).size(),
                preferenceRepository.findByType(PersonPreference.Type.AVOID).size(),
                all.stream().limit(100).map(f -> new FeedbackRow(f.getId(), f.getApplicationId(), f.getUserId(),
                        names.get(f.getUserId()), f.getTableId(), f.getTableRating(), f.getConversationRating(),
                        f.getVenueRating(), f.getRejoinIntent(), f.getRecommendScore(), f.getComment(),
                        f.isPublicReview(), f.isHidden(), likes.getOrDefault(f.getId(), 0L))).toList());
    }

    private static double avg(List<Feedback> list, java.util.function.ToIntFunction<Feedback> f) {
        return list.isEmpty() ? 0 : Math.round(list.stream().mapToInt(f).average().orElse(0) * 100) / 100.0;
    }
}
