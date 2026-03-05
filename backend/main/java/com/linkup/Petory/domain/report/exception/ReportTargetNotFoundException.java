package com.linkup.Petory.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 신고 대상을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class ReportTargetNotFoundException extends ApiException {

    public static final String ERROR_CODE = "REPORT_TARGET_NOT_FOUND";

    public ReportTargetNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public static ReportTargetNotFoundException board() {
        return new ReportTargetNotFoundException("신고 대상 게시글을 찾을 수 없습니다.");
    }

    public static ReportTargetNotFoundException comment() {
        return new ReportTargetNotFoundException("신고 대상 댓글을 찾을 수 없습니다.");
    }

    public static ReportTargetNotFoundException missingPet() {
        return new ReportTargetNotFoundException("신고 대상 실종 제보를 찾을 수 없습니다.");
    }

    public static ReportTargetNotFoundException provider() {
        return new ReportTargetNotFoundException("해당 서비스 제공자를 찾을 수 없습니다.");
    }
}
