package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.service.ConversationCreatorService;
import com.linkup.Petory.domain.chat.service.ConversationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetupChatRoomCreationServiceTest {

    @Mock ConversationCreatorService conversationCreatorService;
    @Mock ConversationService conversationService;
    @InjectMocks MeetupChatRoomCreationService service;

    private static final Long MEETUP_IDX = 1L;
    private static final Long ORGANIZER_IDX = 100L;
    private static final String MEETUP_TITLE = "테스트 모임";

    @Test
    @DisplayName("정상: 채팅방 생성 및 주최자 ADMIN 역할 설정")
    void 정상_채팅방_생성_주최자_ADMIN_역할_설정() {
        service.createChatRoom(MEETUP_IDX, ORGANIZER_IDX, MEETUP_TITLE);

        verify(conversationCreatorService).createConversation(
                ConversationType.MEETUP, RelatedType.MEETUP, MEETUP_IDX,
                MEETUP_TITLE, List.of(ORGANIZER_IDX), ORGANIZER_IDX);
        verify(conversationService).setParticipantRole(
                RelatedType.MEETUP, MEETUP_IDX, ORGANIZER_IDX, ParticipantRole.ADMIN);
    }

    @Test
    @DisplayName("예외: createConversation 실패 시 setParticipantRole 미호출")
    void 예외_createConversation_실패_시_setParticipantRole_미호출() {
        doThrow(new RuntimeException("DB 연결 실패"))
                .when(conversationCreatorService).createConversation(any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> service.createChatRoom(MEETUP_IDX, ORGANIZER_IDX, MEETUP_TITLE));
        verify(conversationService, never()).setParticipantRole(any(), any(), any(), any());
    }

    @Test
    @DisplayName("경계: recover 호출 시 예외 미전파 (로그만)")
    void 경계_recover_호출_시_예외_미전파() {
        assertDoesNotThrow(
                () -> service.recover(new RuntimeException("최종 실패"), MEETUP_IDX, ORGANIZER_IDX, MEETUP_TITLE));
    }
}
