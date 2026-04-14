package com.linkup.Petory.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 임의 메서드 진입 시 DEBUG 한 줄 로그 ({@link com.linkup.Petory.global.aspect.DebugLoggingAspect}).
 * <p>
 * Repository 전역 로그와 맞추려면 {@code tag = "Repository"} 는 피하고,
 * {@code "Service"}, {@code "Chat"} 등 구분 태그를 쓰면 된다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DebugLog {

    /** 로그 본문 (예: {@code "채팅: 내 채팅방 목록 조회"}) */
    String value();

    /**
     * 대괄호 안 태그. 기본 {@code Service}.
     * 출력 형식: {@code DEBUG ... [tag] value}
     */
    String tag() default "Service";
}
