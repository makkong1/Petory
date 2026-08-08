package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.service.CareReviewService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/care-reviews")
@RequiredArgsConstructor
public class CareReviewController {

    private final CareReviewService careReviewService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    /**
     * 리뷰 작성
     *
     * 작성자는 요청 본문이 아니라 인증 주체에서 가져온다. 본문의 reviewerId 를 믿으면
     * 인증만 된 사용자가 남의 이름으로 리뷰를 쓸 수 있다(평점·프로필에 반영되므로 실질 피해).
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareReviewDTO> createReview(@Valid @RequestBody CareReviewDTO dto) {
        Long currentUserId = authenticatedUserIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(careReviewService.createReview(dto, currentUserId));
    }

    /**
     * 특정 사용자(reviewee)에 대한 리뷰 목록 조회
     */
    @GetMapping("/reviewee/{revieweeIdx}")
    public ResponseEntity<List<CareReviewDTO>> getReviewsByReviewee(@PathVariable("revieweeIdx") Long revieweeIdx) {
        return ResponseEntity.ok(careReviewService.getReviewsByReviewee(revieweeIdx));
    }

    /**
     * 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
     */
    @GetMapping("/reviewer/{reviewerIdx}")
    public ResponseEntity<List<CareReviewDTO>> getReviewsByReviewer(@PathVariable("reviewerIdx") Long reviewerIdx) {
        return ResponseEntity.ok(careReviewService.getReviewsByReviewer(reviewerIdx));
    }

    /**
     * 특정 사용자의 평균 평점 조회
     */
    @GetMapping("/average-rating/{revieweeIdx}")
    public ResponseEntity<Double> getAverageRating(@PathVariable("revieweeIdx") Long revieweeIdx) {
        Double average = careReviewService.getAverageRating(revieweeIdx);
        return ResponseEntity.ok(average);
    }
}
