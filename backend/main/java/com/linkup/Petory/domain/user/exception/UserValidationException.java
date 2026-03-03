package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 사용자 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class UserValidationException extends ApiException {

    public static final String ERROR_CODE = "USER_VALIDATION";

    public UserValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static UserValidationException nicknameRequired() {
        return new UserValidationException("닉네임을 입력해주세요.");
    }

    public static UserValidationException nicknameMaxLength() {
        return new UserValidationException("닉네임은 50자 이하여야 합니다.");
    }

    public static UserValidationException emailRequired() {
        return new UserValidationException("이메일을 입력해주세요.");
    }

    public static UserValidationException emailNotRegistered() {
        return new UserValidationException("이메일이 등록되지 않았습니다.");
    }

    public static UserValidationException purposeRequired() {
        return new UserValidationException("인증 용도(purpose)를 지정해주세요.");
    }

    public static UserValidationException petBreedRequired() {
        return new UserValidationException("기타 종류를 선택한 경우, 구체적인 종류를 입력해주세요.");
    }

    public static UserValidationException invalidPurpose(String purposeStr) {
        return new UserValidationException("유효하지 않은 인증 용도입니다: " + purposeStr);
    }
}
