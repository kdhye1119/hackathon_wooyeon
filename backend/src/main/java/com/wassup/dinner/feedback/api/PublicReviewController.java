package com.wassup.dinner.feedback.api;

import com.wassup.dinner.feedback.application.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 비회원 둘러보기: 공개 후기만 로그인 없이 */
@Tag(name = "Public", description = "비회원 둘러보기 (로그인 불필요)")
@RestController
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "공개 후기 (비회원용, sort=recommend / recent)")
    @GetMapping("/api/public/reviews")
    public List<ReviewService.ReviewDto> reviews(@RequestParam(defaultValue = "recommend") String sort,
                                                 @RequestParam(defaultValue = "10") int limit) {
        return reviewService.list(null, sort, limit);
    }
}
