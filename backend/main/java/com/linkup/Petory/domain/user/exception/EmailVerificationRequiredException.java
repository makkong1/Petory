package com.linkup.Petory.domain.user.exception;

import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;

/**
 * 이메일 인증이 필요한 경우 발생하는 예외
 */
public class EmailVerificationRequiredException extends RuntimeException {
    
    private EmailVerificationPurpose purpose;
    
    public EmailVerificationRequiredException(String message) {
        super(message);
    }
    
    public EmailVerificationRequiredException(String message, EmailVerificationPurpose purpose) {
        super(message);
        this.purpose = purpose;
    }
    
    public EmailVerificationRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EmailVerificationRequiredException(String message, EmailVerificationPurpose purpose, Throwable cause) {
        super(message, cause);
        this.purpose = purpose;
    }
    
    public EmailVerificationPurpose getPurpose() {
        return purpose;
    }
}

