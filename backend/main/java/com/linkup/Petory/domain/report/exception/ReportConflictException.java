package com.linkup.Petory.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 이미 해당 대상을 신고했을 때 발생하는 예외.
 * HTTP 409 Conflict
 */
public class ReportConflictException extends ApiException {

    public static final String ERROR_CODE = "REPORT_CONFLICT";

    public ReportConflictException() {
        super("이미 해당 대상을 신고하셨습니다.", HttpStatus.CONFLICT, ERROR_CODE);
    }

    public ReportConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }

    public static ReportConflictException alreadyReported() {
        return new ReportConflictException("이미 해당 대상을 신고하셨습니다.");
    }
}
