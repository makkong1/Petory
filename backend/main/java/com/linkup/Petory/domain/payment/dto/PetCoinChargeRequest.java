package com.linkup.Petory.domain.payment.dto;

/**
 * 코인 충전 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record PetCoinChargeRequest(
    Long userId,       // 관리자용: 지급할 사용자 ID (일반 사용자는 본인 ID)
    Integer amount,    // 충전할 코인 수
    String description // 거래 설명 (선택)
) {}
