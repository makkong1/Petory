package com.linkup.Petory.domain.report.dto;

import com.linkup.Petory.domain.report.entity.ReportTargetType;

/**
 * 신고 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record ReportRequestDTO(
    ReportTargetType targetType,
    Long targetIdx,
    Long reporterId,
    String reason
) {}

