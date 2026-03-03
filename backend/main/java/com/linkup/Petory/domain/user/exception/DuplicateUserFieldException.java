package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 닉네임, 이메일, 사용자명 등 중복 시 발생하는 예외.
 * HTTP 409 Conflict
 */
public class DuplicateUserFieldException extends ApiException {

    public static final String ERROR_CODE = "DUPLICATE_USER_FIELD";

    public enum Field {
        NICKNAME("닉네임"),
        EMAIL("이메일"),
        USERNAME("사용자명"),
        ID("아이디");

        private final String displayName;

        Field(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Field field;

    public DuplicateUserFieldException(Field field) {
        super("이미 사용 중인 " + field.getDisplayName() + "입니다.", HttpStatus.CONFLICT, ERROR_CODE);
        this.field = field;
    }

    /** 알 수 없는 제약조건 위반 시 (Race Condition 등) */
    public DuplicateUserFieldException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
        this.field = null;
    }

    public Field getField() {
        return field;
    }
}
