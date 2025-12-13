package com.linkup.Petory.domain.user.exception;

/**
 * 이메일 인증이 필요한 경우 발생하는 예외
 */
public class EmailVerificationRequiredException extends RuntimeException {
    
    public EmailVerificationRequiredException(String message) {
        super(message);
    }
    
    public EmailVerificationRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

