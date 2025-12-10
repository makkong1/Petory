package com.linkup.Petory.global.websocket.security;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 핸드셰이크 핸들러
 * 인증된 사용자의 Principal을 설정
 */
@Slf4j
public class WebSocketHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        // WebSocketAuthenticationInterceptor에서 저장한 userId 사용
        String userId = (String) attributes.get("userId");

        if (userId != null) {
            log.debug("WebSocket Principal 설정: userId={}", userId);
            return new StompPrincipal(userId);
        }

        log.warn("WebSocket Principal 설정 실패: userId가 없음");
        return null;
    }

}
