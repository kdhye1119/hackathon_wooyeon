package com.wassup.dinner.feedback.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.error.ErrorCode;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.feedback.domain.Feedback;
import com.wassup.dinner.feedback.domain.FeedbackRepository;
import com.wassup.dinner.feedback.domain.ReviewLike;
import com.wassup.dinner.feedback.domain.ReviewLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 홈 화면 공개 후기: 참가자가 공개를 고른 한마디만, 이름 없이 보여준다 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final FeedbackRepository feedbackRepository;
    private final ReviewLikeRepository likeRepository;
    private final AppClock clock;

    public record ReviewDto(Long id, String comment, int recommendScore, long likes, boolean liked, LocalDateTime createdAt) {
    }

    public record LikeResult(long likes, boolean liked) {
    }

    /** sort: recommend(좋아요·추천 의향 순) / recent(최신순) */
    @Transactional(readOnly = true)
    public List<ReviewDto> list(Long userId, String sort, int limit) {
        List<Feedback> shown = feedbackRepository.findAllByOrderByIdDesc().stream()
                .filter(f -> f.isPublicReview() && !f.isHidden() && f.getComment() != null && !f.getComment().isBlank())
                .toList();
        List<ReviewLike> likes = likeRepository.findByFeedbackIdIn(shown.stream().map(Feedback::getId).toList());
        Map<Long, Long> count = likes.stream().collect(Collectors.groupingBy(ReviewLike::getFeedbackId, Collectors.counting()));
        Set<Long> mine = likes.stream().filter(l -> l.getUserId().equals(userId)).map(ReviewLike::getFeedbackId).collect(Collectors.toSet());
        Comparator<ReviewDto> order = "recent".equals(sort)
                ? Comparator.comparing(ReviewDto::createdAt).reversed()
                : Comparator.comparingLong(ReviewDto::likes).reversed()
                        .thenComparing(Comparator.comparingInt(ReviewDto::recommendScore).reversed())
                        .thenComparing(Comparator.comparing(ReviewDto::createdAt).reversed());
        return shown.stream()
                .map(f -> new ReviewDto(f.getId(), f.getComment(), f.getRecommendScore(), count.getOrDefault(f.getId(), 0L),
                        mine.contains(f.getId()), f.getCreatedAt()))
                .sorted(order).limit(Math.max(1, Math.min(limit, 30))).toList();
    }

    /** 좋아요 토글 (공개 중인 후기만) */
    @Transactional
    public LikeResult toggleLike(Long userId, Long feedbackId) {
        Feedback f = feedbackRepository.findById(feedbackId)
                .filter(x -> x.isPublicReview() && !x.isHidden())
                .orElseThrow(() -> ApiException.notFound("후기"));
        var existing = likeRepository.findByFeedbackIdAndUserId(f.getId(), userId);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            likeRepository.flush();
        } else {
            likeRepository.saveAndFlush(new ReviewLike(f.getId(), userId, clock.now()));
        }
        long likes = likeRepository.findByFeedbackIdIn(List.of(f.getId())).size();
        return new LikeResult(likes, existing.isEmpty());
    }

    /** 운영자 노출 관리: 숨김 / 다시 공개 */
    @Transactional
    public void setHidden(Long feedbackId, boolean hidden) {
        Feedback f = feedbackRepository.findById(feedbackId).orElseThrow(() -> ApiException.notFound("후기"));
        if (!f.isPublicReview()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "참가자가 공개를 선택하지 않은 후기예요.");
        }
        f.setHidden(hidden);
    }
}
