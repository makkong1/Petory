package com.linkup.Petory.domain.report.dto;

import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportHandleRequest {
    private ReportStatus status;           // RESOLVED or REJECTED
    private ReportActionType actionTaken;  // NONE/DELETE_CONTENT/SUSPEND_USER/WARN_USER/OTHER
    private String adminNote;
}


