package com.linkup.Petory.global.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * {@code com.linkup.Petory.domain..service} 패키지 타입의 공개 메서드 진입 시
 * {@code [Service] 단순클래스명.메서드명} 형태로 DEBUG 로그 1줄 출력.
 * 메서드별 한글 설명이 필요하면 해당 메서드에 {@link com.linkup.Petory.global.annotation.DebugLog}를 붙이면
 * 본 Aspect는 적용되지 않고 {@link com.linkup.Petory.global.aspect.DebugLoggingAspect}만 동작한다.
 */
@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

    @Around(
            "execution(* com.linkup.Petory.domain..service.*.*(..))"
                    + " && !@annotation(com.linkup.Petory.global.annotation.DebugLog)")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String simpleClass = signature.getDeclaringType().getSimpleName();
        String label = simpleClass + "." + signature.getName();
        log.debug("[Service] {}", label);
        return joinPoint.proceed();
    }
}
