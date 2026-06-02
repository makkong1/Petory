package com.linkup.Petory.domain.report.entity;

/** 신고 처리 후 취한 조치. NONE / DELETE_CONTENT / SUSPEND_USER / WARN_USER / OTHER. */
public enum ReportActionType {
    NONE,
    DELETE_CONTENT,
    SUSPEND_USER,
    WARN_USER,
    OTHER
}

