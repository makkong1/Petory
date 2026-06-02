package com.linkup.Petory.domain.report.dto;

import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;

import lombok.Getter;
import lombok.Setter;

/** 신고 처리 요청 DTO. 처리 결과(RESOLVED/REJECTED)와 취한 조치(DELETE_CONTENT/WARN_USER 등)를 담는다. */
@Getter
@Setter
public class ReportHandleRequest {
    private ReportStatus status;           // RESOLVED or REJECTED
    private ReportActionType actionTaken;  // NONE/DELETE_CONTENT/SUSPEND_USER/WARN_USER/OTHER
    private String adminNote;
}


