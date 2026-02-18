package com.linkup.Petory.domain.meetup.aspect;

import com.linkup.Petory.domain.meetup.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 성능 측정을 위한 AOP Aspect
 * @Timed 어노테이션이 붙은 메서드의 실행 시간을 측정하고 로깅합니다.
 */
@Aspect
@Component
@Slf4j
public class PerformanceAspect {

    @Around("@annotation(com.linkup.Petory.domain.meetup.annotation.Timed)")
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        
        String methodName = timed.value().isEmpty() 
                ? joinPoint.getSignature().toShortString() 
                : timed.value();
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 결과가 List인 경우 크기도 로깅
            if (result instanceof java.util.List) {
                int size = ((java.util.List<?>) result).size();
                log.info("[성능 측정] {} - 실행시간: {}ms, 결과건수: {}", methodName, executionTime, size);
            } else {
                log.info("[성능 측정] {} - 실행시간: {}ms", methodName, executionTime);
            }
            
            return result;
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[성능 측정] {} - 실행시간: {}ms, 에러 발생: {}", methodName, executionTime, e.getMessage());
            throw e;
        }
    }
}
