package com.linkup.Petory.global.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.linkup.Petory.global.websocket.security.WebSocketAuthChannelInterceptor;
import com.linkup.Petory.global.websocket.security.WebSocketAuthenticationInterceptor;
import com.linkup.Petory.global.websocket.security.WebSocketHandshakeHandler;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket 설정
 * STOMP 프로토콜을 사용한 실시간 채팅 구현
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final WebSocketAuthenticationInterceptor authenticationInterceptor;

    /**
     * STOMP 엔드포인트 등록
     * 클라이언트가 WebSocket 연결을 시작할 수 있는 엔드포인트
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 연결 엔드포인트
        // 클라이언트: ws://localhost:8080/ws?token=xxx 또는 ws://localhost:8080/chat?token=xxx
        registry.addEndpoint("/ws", "/chat")
                .setAllowedOriginPatterns("*") // CORS 설정 (개발 환경)
                .addInterceptors(authenticationInterceptor) // 인증 인터셉터 추가
                .setHandshakeHandler(new WebSocketHandshakeHandler()) // Principal 설정
                .withSockJS(); // SockJS 지원 (폴백 옵션)
    }

    /**
     * 메시지 브로커 설정
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버 → 클라이언트 (구독 경로)
        // 클라이언트가 구독할 수 있는 토픽 프리픽스
        registry.enableSimpleBroker("/topic", "/queue", "/user");
        
        // 클라이언트 → 서버 (전송 경로)
        // 클라이언트가 메시지를 보낼 때 사용하는 프리픽스
        registry.setApplicationDestinationPrefixes("/app");
        
        // 사용자별 개인 메시지 프리픽스
        // /user/{userId}/queue/messages 형태로 사용
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 메시지 채널 인터셉터 설정
     * 메시지 전송/구독 전에 인증 체크
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}

