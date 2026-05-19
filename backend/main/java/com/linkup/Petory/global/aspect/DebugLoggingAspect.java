package com.linkup.Petory.global.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.linkup.Petory.global.annotation.DebugLog;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link DebugLog}가 붙은 메서드 실행 직전에 {@code [tag] 설명} 형태로 DEBUG 로그 1줄 출력.
 * Spring AOP이므로 <strong>public</strong> 메서드에만 적용된다.
 * <p>
 * {@code com.linkup.Petory.domain..service} 패키지에서는 {@link ServiceLoggingAspect}와 배타적으로 적용된다.
 * ({@link DebugLog}가 없으면 클래스명과 메서드명만 로깅.)
 */
@Aspect
@Component
@Slf4j
public class DebugLoggingAspect {

    @Around("@annotation(debugLog)")
    public Object logDebugOperation(ProceedingJoinPoint joinPoint, DebugLog debugLog) throws Throwable {
        String message = debugLog.value();
        if (message == null || message.isBlank()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            message = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        }
        log.debug("[{}] {}", debugLog.tag(), message);
        return joinPoint.proceed();
    }
}
