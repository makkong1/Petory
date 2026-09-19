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

    // 화면이 제안 하나를 그리는 데 필요한 케어 요청 정보.
    // 이걸 싣기 전에는 제안마다 getCareRequest 를 한 번 더 불러야 했는데, 한 방에 제안이 여러 건
    // 있을 수 있어(같은 상대에게 케어를 둘 맡기는 경우) 건수만큼 호출이 늘었다.
    private String careRequestTitle;
    private String careRequestStatus;   // OPEN / IN_PROGRESS / COMPLETED / CANCELLED
    private Long requesterId;
    private LocalDateTime requesterCompletedAt;
    private LocalDateTime providerCompletedAt;

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
