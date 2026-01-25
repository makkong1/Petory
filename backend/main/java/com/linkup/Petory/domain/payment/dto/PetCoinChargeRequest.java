package com.linkup.Petory.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 코인 충전 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetCoinChargeRequest {
    private Long userId; // 관리자용: 지급할 사용자 ID (일반 사용자는 본인 ID)
    private Integer amount; // 충전할 코인 수
    private String description; // 거래 설명 (선택)
}
