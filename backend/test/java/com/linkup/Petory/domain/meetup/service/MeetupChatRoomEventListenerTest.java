package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.event.MeetupCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeetupChatRoomEventListenerTest {

    @Mock MeetupChatRoomCreationService meetupChatRoomCreationService;
    @InjectMocks MeetupChatRoomEventListener listener;

    @Test
    @DisplayName("정상: 이벤트 수신 시 CreationService에 위임")
    void 정상_이벤트_수신_시_CreationService_위임() {
        MeetupCreatedEvent event = new MeetupCreatedEvent(this, 1L, 100L, "테스트 모임");

        listener.handleMeetupCreated(event);

        verify(meetupChatRoomCreationService).createChatRoom(1L, 100L, "테스트 모임");
    }
}
