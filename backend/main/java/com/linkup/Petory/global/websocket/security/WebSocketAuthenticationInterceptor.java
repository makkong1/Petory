package com.linkup.Petory.global.websocket.security;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import com.linkup.Petory.util.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 연결 시 JWT 토큰 인증 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * WebSocket 핸드셰이크 전 처리
     * JWT 토큰 검증 및 사용자 인증
     */
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes) throws Exception {
        
        try {
            // 쿼리 파라미터에서 JWT 토큰 추출
            String token = request.getURI().getQuery();
            if (token != null && token.startsWith("token=")) {
                token = token.substring(6); // "token=" 제거
            }

            // 헤더에서 JWT 토큰 추출 (Sec-WebSocket-Protocol 또는 Authorization)
            if (token == null) {
                String authHeader = request.getHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getIdFromToken(token);
                
                if (userId != null) {
                    // 사용자 정보 로드
                    UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
                    
                    // 인증 객체 생성 및 저장
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    // WebSocket 세션에 사용자 ID 저장
                    attributes.put("userId", userId);
                    attributes.put("authentication", authentication);
                    
                    log.info("WebSocket 인증 성공: userId={}", userId);
                    return true;
                }
            }
            
            log.warn("WebSocket 인증 실패: 토큰이 없거나 유효하지 않음");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
            
        } catch (Exception e) {
            log.error("WebSocket 인증 처리 중 오류: {}", e.getMessage(), e);
            response.setStatusCode(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }
    }

    /**
     * WebSocket 핸드셰이크 후 처리
     */
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception) {
        // 핸드셰이크 후 추가 처리 (필요 시)
        if (exception != null) {
            log.error("WebSocket 핸드셰이크 후 오류: {}", exception.getMessage());
        }
    }
}

