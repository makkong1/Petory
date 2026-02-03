package com.linkup.Petory.domain.user.dto;

/**
 * 소셜 로그인 사용자 정보 DTO (record)
 * - 불변 객체로 응답 데이터의 의도치 않은 변경 방지
 */
public record SocialUserDTO(
    Long idx,
    String provider,
    String providerId
) {}
