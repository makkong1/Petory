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

import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.global.security.CustomUserDetails;
import com.linkup.Petory.util.JwtUtil;

import java.util.HashMap;
import java.util.Map;

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

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversation/";
    private static final String USER_DESTINATION_PREFIX = "/user/";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final ConversationParticipantRepository participantRepository;
    private final UsersRepository usersRepository;

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
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            Authentication auth = sessionAttrs != null
                    ? (Authentication) sessionAttrs.get("authentication")
                    : null;

            if (auth == null && token != null && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getIdFromToken(token);

                if (userId != null) {
                    try {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
                        if (!isUsableAccount(userDetails)) {
                            log.warn("WebSocket 메시지 인증 실패: 제재 또는 비활성 계정 userId={}", userId);
                            return null;
                        }

                        auth = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                        // 세션에 저장 (null이면 새 Map 생성)
                        Map<String, Object> attrs = accessor.getSessionAttributes();
                        if (attrs == null) {
                            attrs = new HashMap<>();
                            accessor.setSessionAttributes(attrs);
                        }
                        attrs.put("authentication", auth);
                        attrs.put("userId", userId);

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
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    String userId = attrs != null ? (String) attrs.get("userId") : null;
                    if (userId != null) {
                        accessor.setUser(new StompPrincipal(userId));
                    }
                }
            } else {
                // 인증되지 않은 경우 메시지 차단
                log.warn("WebSocket 메시지 인증 실패: 인증 정보 없음, command={}", command);
                return null;
            }

            // SUBSCRIBE destination 인가 검증 — 인증된 사용자라도 참여한 대화방/본인 큐만 구독 가능
            if (command == StompCommand.SUBSCRIBE && !isSubscriptionAuthorized(accessor)) {
                log.warn("WebSocket 구독 인가 실패: userId={}, destination={}",
                        resolveLoginId(accessor), accessor.getDestination());
                return null;
            }
        }

        return message;
    }

    /**
     * SUBSCRIBE destination 인가 검증.
     * - /topic/conversation/{idx}[/suffix] : 해당 대화방의 ACTIVE 참여자만 허용
     * - /user/{loginId}/... : 본인 큐만 허용
     * - 그 외 destination : 기존 동작 유지 (허용)
     */
    private boolean isSubscriptionAuthorized(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        String loginId = resolveLoginId(accessor);
        if (destination == null || loginId == null) {
            return false;
        }

        if (destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            Long conversationIdx = parseConversationIdx(destination);
            if (conversationIdx == null) {
                return false; // conversation 토픽인데 idx 파싱 불가 → 차단
            }
            Long userIdx = usersRepository.findByIdString(loginId)
                    .map(Users::getIdx)
                    .orElse(null);
            if (userIdx == null) {
                return false;
            }
            return participantRepository.findByConversationIdxAndUserIdx(conversationIdx, userIdx)
                    .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                    .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                    .isPresent();
        }

        if (destination.startsWith(USER_DESTINATION_PREFIX)) {
            return destination.startsWith(USER_DESTINATION_PREFIX + loginId + "/");
        }

        return true;
    }

    private Long parseConversationIdx(String destination) {
        String rest = destination.substring(CONVERSATION_TOPIC_PREFIX.length());
        int slash = rest.indexOf('/');
        String idxPart = slash >= 0 ? rest.substring(0, slash) : rest;
        try {
            return Long.parseLong(idxPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveLoginId(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get("userId") instanceof String loginId) {
            return loginId;
        }
        return accessor.getUser() != null ? accessor.getUser().getName() : null;
    }

    private boolean isUsableAccount(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.isEnabled() && customUserDetails.isAccountNonLocked();
        }
        return userDetails.isEnabled() && userDetails.isAccountNonLocked();
    }
}
