package com.linkup.Petory.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 코인 충전 요청 DTO (record) - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record PetCoinChargeRequest(
        Long userId,
        @NotNull
        @Min(1)
        Integer amount,
        String description
        ) {

}
