package com.linkup.Petory.global.websocket.security;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import com.linkup.Petory.util.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 메시지 전송/구독 시 인증 체크
 * STOMP 명령어(SUBSCRIBE, SEND 등) 실행 전에 사용자 인증 확인
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // CONNECT, SUBSCRIBE, SEND 명령어에 대해서만 인증 체크
        if (command == StompCommand.CONNECT ||
                command == StompCommand.SUBSCRIBE ||
                command == StompCommand.SEND) {

            // JWT 토큰 추출 (헤더 또는 세션에서)
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 세션에서 토큰 또는 인증 정보 가져오기
            Authentication auth = (Authentication) accessor.getSessionAttributes().get("authentication");

            if (auth == null && token != null && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getIdFromToken(token);

                if (userId != null) {
                    try {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
                        auth = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                        // 세션에 저장
                        accessor.getSessionAttributes().put("authentication", auth);
                        accessor.getSessionAttributes().put("userId", userId);

                        // SecurityContext에 설정
                        SecurityContextHolder.getContext().setAuthentication(auth);

                        // Principal 설정 (STOMP에서 사용)
                        accessor.setUser(new StompPrincipal(userId));

                        log.debug("WebSocket 메시지 인증 성공: userId={}, command={}", userId, command);
                    } catch (Exception e) {
                        log.error("WebSocket 메시지 인증 실패: {}", e.getMessage());
                        return null; // 메시지 차단
                    }
                }
            }

            // 이미 인증된 경우
            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Principal이 없으면 설정
                if (accessor.getUser() == null) {
                    String userId = (String) accessor.getSessionAttributes().get("userId");
                    if (userId != null) {
                        accessor.setUser(new StompPrincipal(userId));
                    }
                }
            } else {
                // 인증되지 않은 경우 메시지 차단
                log.warn("WebSocket 메시지 인증 실패: 인증 정보 없음, command={}", command);
                return null;
            }
        }

        return message;
    }

}
