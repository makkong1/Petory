package com.linkup.Petory.global.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.linkup.Petory.global.annotation.RepositoryMethod;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring Data JPA Repository 메서드 실행 시 한 줄 로그 출력.
 * @RepositoryMethod("설명") 있으면 설명 사용, 없으면 Entity.methodName 사용.
 */
@Aspect
@Component
@Slf4j
public class RepositoryLoggingAspect {

    @Around("execution(* com.linkup.Petory.domain..repository.SpringDataJpa*.*(..))")
    public Object logRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method method = signature.getMethod();

        String label;
        RepositoryMethod ann = method.getAnnotation(RepositoryMethod.class);
        if (ann != null && !ann.value().isBlank()) {
            label = ann.value();
        } else {
            String declaringType = signature.getDeclaringTypeName();
            String className = declaringType.substring(declaringType.lastIndexOf('.') + 1);
            String methodName = signature.getName();
            String entityName = className
                    .replaceFirst("^SpringDataJpa", "")
                    .replaceFirst("Repository$", "");
            label = entityName + "." + methodName;
        }

        log.debug("[Repository] {}", label);
        return joinPoint.proceed();
    }
}
