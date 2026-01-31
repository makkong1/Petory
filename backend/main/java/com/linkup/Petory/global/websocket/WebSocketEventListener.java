package com.linkup.Petory.global.websocket;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 연결/해제 이벤트 리스너
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    // private final SimpMessageSendingOperations messagingTemplate;

    /**
     * WebSocket 연결 성공 시
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal != null) {
            log.info("WebSocket 연결 성공: userId={}, sessionId={}",
                    principal.getName(), headerAccessor.getSessionId());
        } else {
            log.warn("WebSocket 연결 성공했으나 사용자 정보 없음: sessionId={}",
                    headerAccessor.getSessionId());
        }
    }

    /**
     * WebSocket 연결 해제 시
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal != null) {
            log.info("WebSocket 연결 해제: userId={}, sessionId={}",
                    principal.getName(), headerAccessor.getSessionId());

            // 연결 해제 알림을 필요로 하는 경우
            // messagingTemplate.convertAndSend("/topic/user." + principal.getName() +
            // ".disconnect",
            // "사용자 연결이 해제되었습니다.");
        } else {
            log.warn("WebSocket 연결 해제했으나 사용자 정보 없음: sessionId={}",
                    headerAccessor.getSessionId());
        }
    }
}
