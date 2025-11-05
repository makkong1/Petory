package com.linkup.Petory.dto;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareApplicationDTO {
    private Long idx;
    private Long careRequestId;
    private String message;
    private String status; // PENDING, ACCEPTED, REJECTED
    private LocalDateTime createdAt;

    // 지원자 정보
    private Long applicantId;
    private String applicantName;
    private String applicantLocation;

    // Provider 정보 (추가)
    private Long providerId;
    private String providerName;

    // 리뷰 정보
    private java.util.List<CareReviewDTO> reviews;
}