package com.linkup.Petory.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 권한 거부 예외 처리
     * SSE 연결 등에서 발생하는 AuthorizationDeniedException 처리
     * 응답이 이미 커밋된 경우는 로그만 남기고 조용히 처리
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationDeniedException(AuthorizationDeniedException e) {
        // 응답이 이미 커밋된 경우는 로그만 남기고 조용히 처리
        log.debug("권한 거부 (응답 커밋 후): {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "권한이 없습니다.");
        response.put("message", "해당 리소스에 접근할 권한이 없습니다.");
        response.put("status", HttpStatus.FORBIDDEN.value());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 비동기 요청 타임아웃 예외 처리
     * SSE 연결 타임아웃은 정상적인 동작이므로 로그 레벨을 낮춤
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        // SSE 타임아웃은 정상적인 동작이므로 DEBUG 레벨로만 로깅
        log.debug("비동기 요청 타임아웃 (SSE 연결 종료): {}", e.getMessage());
        // 응답이 이미 커밋되었을 수 있으므로 void 반환
    }

    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "잘못된 요청입니다.");
        response.put("message", e.getMessage());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * IllegalStateException 처리
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        log.warn("상태 오류: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "요청을 처리할 수 없습니다.");
        response.put("message", e.getMessage());
        response.put("status", HttpStatus.CONFLICT.value());
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 이메일 인증 필요 예외 처리
     * 프론트엔드에서 이 예외를 감지하여 이메일 인증 페이지로 리다이렉트
     */
    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleEmailVerificationRequiredException(EmailVerificationRequiredException e) {
        log.info("이메일 인증 필요: {}, purpose: {}", e.getMessage(), e.getPurpose());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "이메일 인증이 필요합니다.");
        response.put("message", e.getMessage());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("errorCode", "EMAIL_VERIFICATION_REQUIRED");
        response.put("redirectUrl", "/email-verification");
        if (e.getPurpose() != null) {
            response.put("purpose", e.getPurpose().name());
        }
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 기타 예외 처리
     * AsyncRequestTimeoutException은 위에서 처리하므로 제외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        // AsyncRequestTimeoutException은 이미 처리했으므로 제외
        if (e instanceof AsyncRequestTimeoutException) {
            return null;
        }
        
        log.error("예상치 못한 오류 발생", e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "서버 오류가 발생했습니다.");
        response.put("message", "잠시 후 다시 시도해주세요.");
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

