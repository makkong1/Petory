package com.linkup.Petory.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Repository 메서드 실행 시 로그에 표시할 한 줄 설명.
 * RepositoryLoggingAspect에서 사용.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepositoryMethod {
    String value();
}
