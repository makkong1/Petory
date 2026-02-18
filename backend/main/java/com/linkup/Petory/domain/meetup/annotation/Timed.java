package com.linkup.Petory.domain.meetup.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 성능 측정을 위한 어노테이션
 * 이 어노테이션이 붙은 메서드는 PerformanceAspect에서 자동으로 실행 시간을 측정합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {
    /**
     * 성능 측정 로그에 표시할 메서드 이름
     * 기본값은 메서드명입니다.
     */
    String value() default "";
}
