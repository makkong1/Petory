package com.linkup.Petory.global.websocket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private MessageChannel channel;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private WebSocketAuthChannelInterceptor interceptor() {
        return new WebSocketAuthChannelInterceptor(
                jwtUtil, userDetailsService, participantRepository, usersRepository);
    }

    /** 인증이 이미 완료된 세션의 SUBSCRIBE 메시지를 생성한다. */
    private Message<byte[]> subscribeMessage(String loginId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("authentication", mock(Authentication.class));
        attrs.put("userId", loginId);
        accessor.setSessionAttributes(attrs);
        // 실제 STOMP 인바운드 채널과 동일하게 mutable 헤더 유지 (인터셉터가 setUser를 호출함)
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ConversationParticipant activeParticipant() {
        return ConversationParticipant.builder()
                .status(ParticipantStatus.ACTIVE)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("정상: ACTIVE 참여자는 자신의 대화방 토픽을 구독할 수 있다")
    void 정상_참여자_대화방_구독_허용() {
        when(usersRepository.findByIdString("member"))
                .thenReturn(Optional.of(Users.builder().idx(10L).id("member").build()));
        when(participantRepository.findByConversationIdxAndUserIdx(5L, 10L))
                .thenReturn(Optional.of(activeParticipant()));

        Message<?> result = interceptor().preSend(subscribeMessage("member", "/topic/conversation/5"), channel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("예외: 비참여자는 타인의 대화방 토픽을 구독할 수 없다 (도청 차단)")
    void 예외_비참여자_대화방_구독_차단() {
        when(usersRepository.findByIdString("intruder"))
                .thenReturn(Optional.of(Users.builder().idx(99L).id("intruder").build()));
        when(participantRepository.findByConversationIdxAndUserIdx(5L, 99L))
                .thenReturn(Optional.empty());

        Message<?> result = interceptor().preSend(subscribeMessage("intruder", "/topic/conversation/5"), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("예외: LEFT 상태 참여자는 대화방 토픽을 구독할 수 없다")
    void 예외_비활성_참여자_구독_차단() {
        when(usersRepository.findByIdString("left-user"))
                .thenReturn(Optional.of(Users.builder().idx(20L).id("left-user").build()));
        when(participantRepository.findByConversationIdxAndUserIdx(5L, 20L))
                .thenReturn(Optional.of(ConversationParticipant.builder()
                        .status(ParticipantStatus.LEFT)
                        .isDeleted(false)
                        .build()));

        Message<?> result = interceptor().preSend(subscribeMessage("left-user", "/topic/conversation/5"), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("예외: 타인의 /user 큐는 구독할 수 없다")
    void 예외_타인_유저큐_구독_차단() {
        Message<?> result = interceptor().preSend(
                subscribeMessage("me", "/user/victim/queue/errors"), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("정상: 본인의 /user 큐는 구독할 수 있다")
    void 정상_본인_유저큐_구독_허용() {
        Message<?> result = interceptor().preSend(
                subscribeMessage("me", "/user/me/queue/errors"), channel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("예외: conversation 토픽인데 idx 파싱이 불가능하면 차단한다")
    void 예외_잘못된_대화방_토픽_차단() {
        Message<?> result = interceptor().preSend(
                subscribeMessage("me", "/topic/conversation/abc"), channel);

        assertThat(result).isNull();
    }
}
