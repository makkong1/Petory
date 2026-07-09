package com.linkup.Petory.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 * - confirmReactivate: 휴면 계정 로그인 시 본인이 재활성화에 동의했는지 여부 (기본 false)
 */
public record LoginRequest(
    @NotBlank String id,
    @NotBlank String password,
    boolean confirmReactivate
) {}
