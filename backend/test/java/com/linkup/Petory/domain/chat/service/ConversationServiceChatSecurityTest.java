package com.linkup.Petory.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.chat.converter.ChatMessageConverter;
import com.linkup.Petory.domain.chat.converter.ConversationConverter;
import com.linkup.Petory.domain.chat.converter.ConversationParticipantConverter;
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.exception.ChatForbiddenException;
import com.linkup.Petory.domain.chat.exception.ChatValidationException;
import com.linkup.Petory.domain.chat.repository.ChatMessageRepository;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.payment.service.PetCoinEscrowService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 채팅 코드리뷰(2026-04-14) 반영: PATCH 상태 변경 시 ACTIVE 참여자만 허용,
 * {@code createMissingPetChat} 제보자=목격자 차단 및 기존 방 재사용 경로.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class ConversationServiceChatSecurityTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private ConversationConverter conversationConverter;
    @Mock
    private ConversationParticipantConverter participantConverter;
    @Mock
    private ChatMessageConverter messageConverter;
    @Mock
    private CareRequestRepository careRequestRepository;
    @Mock
    private CareApplicationRepository careApplicationRepository;
    @Mock
    private PetCoinEscrowService petCoinEscrowService;
    @Mock
    private ConversationCreatorService conversationCreatorService;
    @Mock
    private MeetupParticipantsRepository meetupParticipantsRepository;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    @DisplayName("정상: ACTIVE 참여자는 채팅방 상태 변경 가능")
    void 정상_updateConversationStatus_ACTIVE참여자() {
        long convIdx = 10L;
        long userId = 200L;

        ConversationParticipant active = ConversationParticipant.builder()
                .status(ParticipantStatus.ACTIVE)
                .isDeleted(false)
                .build();

        when(participantRepository.findByConversationIdxAndUserIdx(convIdx, userId))
                .thenReturn(Optional.of(active));

        Conversation conversation = Conversation.builder()
                .idx(convIdx)
                .status(ConversationStatus.ACTIVE)
                .build();
        when(conversationRepository.findById(convIdx)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationDTO expected = ConversationDTO.builder().idx(convIdx).status("CLOSED").build();
        when(conversationConverter.toDTO(any(Conversation.class))).thenReturn(expected);

        ConversationDTO result = conversationService.updateConversationStatus(
                convIdx, ConversationStatus.CLOSED, userId);

        assertThat(result).isSameAs(expected);
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    @DisplayName("예외: 비참여자·LEFT는 updateConversationStatus 불가")
    void 예외_updateConversationStatus_비활성참여자() {
        long convIdx = 10L;
        long userId = 200L;

        ConversationParticipant left = ConversationParticipant.builder()
                .status(ParticipantStatus.LEFT)
                .isDeleted(false)
                .build();

        when(participantRepository.findByConversationIdxAndUserIdx(convIdx, userId))
                .thenReturn(Optional.of(left));

        assertThatThrownBy(() -> conversationService.updateConversationStatus(
                convIdx, ConversationStatus.CLOSED, userId))
                .isInstanceOf(ChatForbiddenException.class);
    }

    @Test
    @DisplayName("예외: createMissingPetChat 제보자와 목격자가 같으면 불가")
    void 예외_createMissingPetChat_본인제보() {
        assertThatThrownBy(() -> conversationService.createMissingPetChat(1L, 99L, 99L))
                .isInstanceOf(ChatValidationException.class)
                .hasMessageContaining("본인");
    }

    @Test
    @DisplayName("정상: 실종 채팅 기존 방에 제보자·목격자 모두 있으면 새로 만들지 않음")
    void 정상_createMissingPetChat_기존방재사용() {
        long boardIdx = 300L;
        long reporterId = 1L;
        long witnessId = 2L;

        Conversation conv = Conversation.builder().idx(1000L).relatedIdx(boardIdx).build();
        Users reporter = Users.builder().idx(reporterId).build();
        Users witness = Users.builder().idx(witnessId).build();

        ConversationParticipant p1 = ConversationParticipant.builder()
                .conversation(conv)
                .user(reporter)
                .status(ParticipantStatus.ACTIVE)
                .build();
        ConversationParticipant p2 = ConversationParticipant.builder()
                .conversation(conv)
                .user(witness)
                .status(ParticipantStatus.ACTIVE)
                .build();

        when(conversationRepository.findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
                eq(RelatedType.MISSING_PET_BOARD), eq(List.of(boardIdx))))
                .thenReturn(List.of(conv));
        when(participantRepository.findParticipantsByConversationIdxsAndStatus(
                eq(List.of(1000L)), eq(ParticipantStatus.ACTIVE)))
                .thenReturn(List.of(p1, p2));

        ConversationDTO dto = ConversationDTO.builder().idx(1000L).build();
        when(conversationConverter.toDTO(conv)).thenReturn(dto);

        assertThat(conversationService.createMissingPetChat(boardIdx, reporterId, witnessId))
                .isSameAs(dto);

        verify(conversationCreatorService, never())
                .createConversation(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("정상: 실종 채팅방이 없으면 ConversationCreatorService에 위임")
    void 정상_createMissingPetChat_신규생성위임() {
        long boardIdx = 400L;
        long reporterId = 10L;
        long witnessId = 20L;

        when(conversationRepository.findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
                eq(RelatedType.MISSING_PET_BOARD), eq(List.of(boardIdx))))
                .thenReturn(List.of());

        ConversationDTO created = ConversationDTO.builder().idx(55L).build();
        when(conversationCreatorService.createConversation(
                eq(ConversationType.MISSING_PET),
                eq(RelatedType.MISSING_PET_BOARD),
                eq(boardIdx),
                isNull(),
                eq(List.of(reporterId, witnessId)),
                eq(witnessId)))
                .thenReturn(created);

        assertThat(conversationService.createMissingPetChat(boardIdx, reporterId, witnessId))
                .isSameAs(created);
    }

    @Test
    @DisplayName("예외: 모임 비참여자는 joinMeetupChat 불가")
    void 예외_joinMeetupChat_모임비참여자() {
        long meetupIdx = 77L;
        long userId = 300L;

        when(meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userId))
                .thenReturn(false);

        assertThatThrownBy(() -> conversationService.joinMeetupChat(meetupIdx, userId))
                .isInstanceOf(ChatForbiddenException.class)
                .hasMessageContaining("모임 참여자");
    }
}
