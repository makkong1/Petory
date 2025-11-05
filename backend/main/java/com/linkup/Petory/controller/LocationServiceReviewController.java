package com.linkup.Petory.controller;

import com.linkup.Petory.dto.LocationServiceReviewDTO;
import com.linkup.Petory.service.LocationServiceReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/location-service-reviews")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class LocationServiceReviewController {

    private final LocationServiceReviewService reviewService;

    // 리뷰 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> createReview(@RequestBody LocationServiceReviewDTO reviewDTO) {
        try {
            LocationServiceReviewDTO createdReview = reviewService.createReview(reviewDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("review", createdReview);
            response.put("message", "리뷰가 성공적으로 작성되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("리뷰 생성 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 리뷰 수정
    @PutMapping("/{reviewIdx}")
    public ResponseEntity<Map<String, Object>> updateReview(@PathVariable Long reviewIdx,
            @RequestBody LocationServiceReviewDTO reviewDTO) {
        try {
            LocationServiceReviewDTO updatedReview = reviewService.updateReview(reviewIdx, reviewDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("review", updatedReview);
            response.put("message", "리뷰가 성공적으로 수정되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("리뷰 수정 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 리뷰 삭제
    @DeleteMapping("/{reviewIdx}")
    public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable Long reviewIdx) {
        try {
            reviewService.deleteReview(reviewIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "리뷰가 성공적으로 삭제되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("리뷰 삭제 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 특정 서비스의 리뷰 목록 조회
    @GetMapping("/service/{serviceIdx}")
    public ResponseEntity<Map<String, Object>> getReviewsByService(@PathVariable Long serviceIdx) {
        try {
            List<LocationServiceReviewDTO> reviews = reviewService.getReviewsByService(serviceIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("reviews", reviews);
            response.put("count", reviews.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 리뷰 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 특정 사용자의 리뷰 목록 조회
    @GetMapping("/user/{userIdx}")
    public ResponseEntity<Map<String, Object>> getReviewsByUser(@PathVariable Long userIdx) {
        try {
            List<LocationServiceReviewDTO> reviews = reviewService.getReviewsByUser(userIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("reviews", reviews);
            response.put("count", reviews.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("사용자 리뷰 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
