package com.wassup.dinner.feedback.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    Optional<ReviewLike> findByFeedbackIdAndUserId(Long feedbackId, Long userId);

    List<ReviewLike> findByFeedbackIdIn(Collection<Long> feedbackIds);
}
