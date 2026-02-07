package com.linkup.Petory.domain.user.dto;

/**
 * 로그인 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record LoginRequest(
    String id,       // 로그인용 아이디
    String password
) {}
