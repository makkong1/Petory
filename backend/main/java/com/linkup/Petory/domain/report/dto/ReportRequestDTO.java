package com.linkup.Petory.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.linkup.Petory.domain.report.entity.ReportTargetType;

/**
 * 신고 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record ReportRequestDTO(
    @NotNull ReportTargetType targetType,
    @NotNull Long targetIdx,
    /** 신고자는 인증 주체에서 결정하며, 이 요청 바디 값은 서버에서 사용하지 않는다. */
    Long reporterId,
    @NotBlank String reason
) {}
