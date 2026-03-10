package com.linkup.Petory.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 신고 도메인 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class ReportValidationException extends ApiException {

    public static final String ERROR_CODE = "REPORT_VALIDATION";

    public ReportValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static ReportValidationException targetTypeRequired() {
        return new ReportValidationException("신고 대상 종류를 선택해주세요.");
    }

    public static ReportValidationException targetIdxRequired() {
        return new ReportValidationException("신고 대상 ID가 필요합니다.");
    }

    public static ReportValidationException reporterRequired() {
        return new ReportValidationException("신고자 정보가 필요합니다.");
    }

    public static ReportValidationException reasonRequired() {
        return new ReportValidationException("신고 사유를 입력해주세요.");
    }

    public static ReportValidationException statusRequired() {
        return new ReportValidationException("처리 상태를 선택해주세요.");
    }

    public static ReportValidationException unsupportedTarget() {
        return new ReportValidationException("지원하지 않는 신고 대상입니다.");
    }
}
