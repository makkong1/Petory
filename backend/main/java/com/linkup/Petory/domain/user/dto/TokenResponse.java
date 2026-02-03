package com.linkup.Petory.domain.user.dto;

/**
 * 토큰 응답 DTO (record)
 * - 불변 객체로 응답 데이터의 의도치 않은 변경 방지
 */
public record TokenResponse(
    String accessToken,
    String refreshToken,
    UsersDTO user
) {}
