package com.linkup.Petory.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.linkup.Petory.domain.chat.converter.ChatMessageConverter;
import com.linkup.Petory.domain.chat.dto.ChatMessageDTO;
import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.exception.ChatForbiddenException;
import com.linkup.Petory.domain.chat.repository.ChatMessageRepository;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 채팅 코드리뷰(2026-04-14) 반영: {@code requireActiveParticipant}로 /before·/search·unread 등
 * 비참여자 접근 차단 검증.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class ChatMessageServiceTest {

    private static final long CONV = 50L;
    private static final long USER = 900L;

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private ChatMessageConverter messageConverter;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("정상: ACTIVE 참여자는 getMessagesBefore 조회 가능")
    void 정상_getMessagesBefore_ACTIVE참여자() {
        ConversationParticipant active = participant(ParticipantStatus.ACTIVE, false);
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.of(active));

        LocalDateTime before = LocalDateTime.of(2026, 4, 14, 12, 0);
        when(chatMessageRepository.findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(CONV), eq(before), any(PageRequest.class)))
                .thenReturn(List.of());

        assertThat(chatMessageService.getMessagesBefore(CONV, USER, before, 30)).isEmpty();
        verify(chatMessageRepository).findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(CONV), eq(before), any(PageRequest.class));
    }

    @Test
    @DisplayName("예외: 비참여자는 getMessagesBefore 불가 (ChatForbiddenException)")
    void 예외_getMessagesBefore_비참여자() {
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getMessagesBefore(
                CONV, USER, LocalDateTime.now(), 20))
                .isInstanceOf(ChatForbiddenException.class)
                .hasMessageContaining("참여");
    }

    @Test
    @DisplayName("예외: LEFT 참여자는 getMessagesBefore 불가")
    void 예외_getMessagesBefore_LEFT참여자() {
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.of(participant(ParticipantStatus.LEFT, false)));

        assertThatThrownBy(() -> chatMessageService.getMessagesBefore(
                CONV, USER, LocalDateTime.now(), 20))
                .isInstanceOf(ChatForbiddenException.class)
                .hasMessageContaining("참여");
    }

    @Test
    @DisplayName("예외: 소프트삭제된 참여자 레코드는 getMessagesBefore 불가")
    void 예외_getMessagesBefore_삭제된참여자() {
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.of(participant(ParticipantStatus.ACTIVE, true)));

        assertThatThrownBy(() -> chatMessageService.getMessagesBefore(
                CONV, USER, LocalDateTime.now(), 20))
                .isInstanceOf(ChatForbiddenException.class);
    }

    @Test
    @DisplayName("정상: ACTIVE 참여자는 searchMessages 가능")
    void 정상_searchMessages_ACTIVE참여자() {
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.of(participant(ParticipantStatus.ACTIVE, false)));

        ChatMessage msg = ChatMessage.builder().build();
        when(chatMessageRepository.searchMessagesByKeyword(CONV, "lost"))
                .thenReturn(List.of(msg));
        when(messageConverter.toDTO(msg)).thenReturn(ChatMessageDTO.builder().idx(1L).build());

        assertThat(chatMessageService.searchMessages(CONV, USER, "lost"))
                .hasSize(1)
                .extracting(ChatMessageDTO::getIdx)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("예외: 비참여자는 searchMessages 불가")
    void 예외_searchMessages_비참여자() {
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.searchMessages(CONV, USER, "x"))
                .isInstanceOf(ChatForbiddenException.class);
    }

    @Test
    @DisplayName("경계: getUnreadCount에서 unreadCount null이면 0")
    void 경계_getUnreadCount_unreadCount_null() {
        ConversationParticipant p = participant(ParticipantStatus.ACTIVE, false);
        p.setUnreadCount(null);
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.of(p));

        assertThat(chatMessageService.getUnreadCount(CONV, USER)).isZero();
    }

    @Test
    @DisplayName("정상: getUnreadCount는 참여자 unreadCount 반환")
    void 정상_getUnreadCount() {
        ConversationParticipant p = participant(ParticipantStatus.ACTIVE, false);
        p.setUnreadCount(7);
        when(participantRepository.findByConversationIdxAndUserIdx(CONV, USER))
                .thenReturn(Optional.of(p));

        assertThat(chatMessageService.getUnreadCount(CONV, USER)).isEqualTo(7L);
    }

    private static ConversationParticipant participant(ParticipantStatus status, boolean deleted) {
        Conversation c = Conversation.builder().idx(CONV).build();
        Users u = Users.builder().idx(USER).build();
        return ConversationParticipant.builder()
                .conversation(c)
                .user(u)
                .status(status)
                .isDeleted(deleted)
                .build();
    }
}
