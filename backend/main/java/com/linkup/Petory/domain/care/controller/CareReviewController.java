package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.service.CareReviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/care-reviews")
@RequiredArgsConstructor
public class CareReviewController {

    private final CareReviewService careReviewService;

    /**
     * 리뷰 작성
     */
    @PostMapping
    public ResponseEntity<CareReviewDTO> createReview(@RequestBody CareReviewDTO dto) {
        return ResponseEntity.ok(careReviewService.createReview(dto));
    }

    /**
     * 특정 사용자(reviewee)에 대한 리뷰 목록 조회
     */
    @GetMapping("/reviewee/{revieweeIdx}")
    public ResponseEntity<List<CareReviewDTO>> getReviewsByReviewee(@PathVariable Long revieweeIdx) {
        return ResponseEntity.ok(careReviewService.getReviewsByReviewee(revieweeIdx));
    }

    /**
     * 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
     */
    @GetMapping("/reviewer/{reviewerIdx}")
    public ResponseEntity<List<CareReviewDTO>> getReviewsByReviewer(@PathVariable Long reviewerIdx) {
        return ResponseEntity.ok(careReviewService.getReviewsByReviewer(reviewerIdx));
    }

    /**
     * 특정 사용자의 평균 평점 조회
     */
    @GetMapping("/average-rating/{revieweeIdx}")
    public ResponseEntity<Double> getAverageRating(@PathVariable Long revieweeIdx) {
        Double average = careReviewService.getAverageRating(revieweeIdx);
        return ResponseEntity.ok(average);
    }
}
