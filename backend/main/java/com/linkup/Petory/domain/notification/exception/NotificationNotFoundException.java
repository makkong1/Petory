package com.linkup.Petory.domain.notification.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 알림을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class NotificationNotFoundException extends ApiException {

    public static final String ERROR_CODE = "NOTIFICATION_NOT_FOUND";

    public NotificationNotFoundException() {
        super("알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public NotificationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
