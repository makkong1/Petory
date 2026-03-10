package com.linkup.Petory.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 신고 정보를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class ReportNotFoundException extends ApiException {

    public static final String ERROR_CODE = "REPORT_NOT_FOUND";

    public ReportNotFoundException() {
        super("신고 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public ReportNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
