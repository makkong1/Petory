package com.linkup.Petory.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 신고 도메인 권한 부족 시 발생하는 예외.
 * HTTP 400 Bad Request (역할 검증)
 */
public class ReportForbiddenException extends ApiException {

    public static final String ERROR_CODE = "REPORT_FORBIDDEN";

    public ReportForbiddenException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static ReportForbiddenException providerOnly() {
        return new ReportForbiddenException("서비스 제공자만 신고할 수 있습니다.");
    }
}
