package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
/**
 * 펫케어 지원 응답/요청 DTO. 지원자 정보와 지원 상태(PENDING/ACCEPTED/REJECTED)를 포함한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareApplicationDTO {

    private Long idx;
    private Long careRequestId;
    private String message;
    private String status; // PENDING, ACCEPTED, REJECTED
    private LocalDateTime createdAt;

    /** 제안 시점의 제시 금액. 제공자가 수락 화면에서 보는 금액이다. */
    private Integer offeredCoins;

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
