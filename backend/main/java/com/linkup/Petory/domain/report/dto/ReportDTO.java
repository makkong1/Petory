package com.linkup.Petory.domain.report.dto;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReportDTO {
    Long idx;
    ReportTargetType targetType;
    Long targetIdx;
    Long reporterId;
    String reporterName;
    String reason;
    ReportStatus status;
    ReportActionType actionTaken;
    Long handledBy;
    String handledByName;
    LocalDateTime handledAt;
    String adminNote;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Integer reportCount; // 같은 target에 대한 총 신고 횟수 (null 가능)
}

