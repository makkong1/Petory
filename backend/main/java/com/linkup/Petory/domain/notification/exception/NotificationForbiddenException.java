package com.linkup.Petory.domain.notification.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 알림 도메인 권한 부족 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class NotificationForbiddenException extends ApiException {

    public static final String ERROR_CODE = "NOTIFICATION_FORBIDDEN";

    public NotificationForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static NotificationForbiddenException ownNotificationOnly() {
        return new NotificationForbiddenException("본인의 알림만 읽음 처리할 수 있습니다.");
    }
}
