package com.linkup.Petory.domain.report.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.entity.Report;

@Component
public class ReportConverter {

    public ReportDTO toDTO(Report report) {
        if (report == null) {
            return null;
        }
        return ReportDTO.builder()
                .idx(report.getIdx())
                .targetType(report.getTargetType())
                .targetIdx(report.getTargetIdx())
                .reporterId(report.getReporter() != null ? report.getReporter().getIdx() : null)
                .reporterName(report.getReporter() != null ? report.getReporter().getUsername() : null)
                .reason(report.getReason())
                .status(report.getStatus())
                .actionTaken(report.getActionTaken())
                .handledBy(report.getHandledBy() != null ? report.getHandledBy().getIdx() : null)
                .handledByName(report.getHandledBy() != null ? report.getHandledBy().getUsername() : null)
                .handledAt(report.getHandledAt())
                .adminNote(report.getAdminNote())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .reportCount(null) // 신고 횟수는 서비스 레이어에서 계산
                .build();
    }
}

