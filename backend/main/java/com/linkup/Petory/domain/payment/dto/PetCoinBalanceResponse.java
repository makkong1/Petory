package com.linkup.Petory.domain.payment.dto;

/**
 * 코인 잔액 응답 DTO (record)
 * - 불변 객체로 응답 데이터의 의도치 않은 변경 방지
 */
public record PetCoinBalanceResponse(
    Long userId,
    Integer balance
) {}
