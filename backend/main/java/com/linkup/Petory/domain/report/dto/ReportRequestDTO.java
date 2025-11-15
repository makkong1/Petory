package com.linkup.Petory.domain.report.dto;

import com.linkup.Petory.domain.report.entity.ReportTargetType;

import lombok.Data;

@Data
public class ReportRequestDTO {
    private ReportTargetType targetType;
    private Long targetIdx;
    private Long reporterId;
    private String reason;
}

