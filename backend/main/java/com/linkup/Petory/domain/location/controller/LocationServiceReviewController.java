package com.linkup.Petory.domain.location.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.service.LocationServiceReviewService;
import com.linkup.Petory.global.exception.ApiException;
import com.linkup.Petory.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 위치 기반 서비스(동물병원·펫숍 등) 리뷰 작성·조회·삭제 API.
 */
@Slf4j
@RestController
@RequestMapping("/api/location-service-reviews")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class LocationServiceReviewController {

    private final LocationServiceReviewService reviewService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LocationServiceReviewDTO reviewDTO) {
        try {
            LocationServiceReviewDTO createdReview = reviewService.createReview(reviewDTO, userDetails.getLoginId());

            Map<String, Object> response = new HashMap<>();
            response.put("review", createdReview);
            response.put("message", "리뷰가 성공적으로 작성되었습니다.");

            return ResponseEntity.ok(response);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("리뷰 생성 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{reviewIdx}")
    public ResponseEntity<Map<String, Object>> updateReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("reviewIdx") Long reviewIdx,
            @Valid @RequestBody LocationServiceReviewDTO reviewDTO) {
        try {
            LocationServiceReviewDTO updatedReview = reviewService.updateReview(reviewIdx, reviewDTO,
                    userDetails.getLoginId());

            Map<String, Object> response = new HashMap<>();
            response.put("review", updatedReview);
            response.put("message", "리뷰가 성공적으로 수정되었습니다.");

            return ResponseEntity.ok(response);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("리뷰 수정 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{reviewIdx}")
    public ResponseEntity<Map<String, Object>> deleteReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("reviewIdx") Long reviewIdx) {
        try {
            reviewService.deleteReview(reviewIdx, userDetails.getLoginId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "리뷰가 성공적으로 삭제되었습니다.");

            return ResponseEntity.ok(response);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("리뷰 삭제 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/service/{serviceIdx}")
    public ResponseEntity<Map<String, Object>> getReviewsByService(@PathVariable("serviceIdx") Long serviceIdx) {
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

    @GetMapping("/user/{userIdx}")
    public ResponseEntity<Map<String, Object>> getReviewsByUser(@PathVariable("userIdx") Long userIdx) {
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
