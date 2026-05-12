package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.user.entity.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetupChatRoomRecoverySchedulerTest {

    @Mock MeetupRepository meetupRepository;
    @Mock MeetupChatRoomCreationService meetupChatRoomCreationService;
    @InjectMocks MeetupChatRoomRecoveryScheduler scheduler;

    @Test
    @DisplayName("정상: 채팅방 없는 모임 감지 시 채팅방 생성")
    void 정상_채팅방_없는_모임_복구() {
        Users organizer = mock(Users.class);
        when(organizer.getIdx()).thenReturn(100L);
        Meetup meetup = mock(Meetup.class);
        when(meetup.getIdx()).thenReturn(1L);
        when(meetup.getOrganizer()).thenReturn(organizer);
        when(meetup.getTitle()).thenReturn("복구 모임");
        when(meetupRepository.findWithoutChatRoom()).thenReturn(List.of(meetup));

        scheduler.recoverMissingChatRooms();

        verify(meetupChatRoomCreationService).createChatRoom(1L, 100L, "복구 모임");
    }

    @Test
    @DisplayName("경계: 채팅방 없는 모임이 없으면 createChatRoom 미호출")
    void 경계_채팅방_없는_모임_없으면_스킵() {
        when(meetupRepository.findWithoutChatRoom()).thenReturn(List.of());

        scheduler.recoverMissingChatRooms();

        verify(meetupChatRoomCreationService, never()).createChatRoom(any(), any(), any());
    }

    @Test
    @DisplayName("예외: 개별 복구 실패해도 나머지 모임 복구 계속 진행")
    void 예외_개별_복구_실패해도_나머지_모임_복구_계속() {
        Users organizer1 = mock(Users.class);
        when(organizer1.getIdx()).thenReturn(100L);
        Meetup meetup1 = mock(Meetup.class);
        when(meetup1.getIdx()).thenReturn(1L);
        when(meetup1.getOrganizer()).thenReturn(organizer1);
        when(meetup1.getTitle()).thenReturn("모임1");

        Users organizer2 = mock(Users.class);
        when(organizer2.getIdx()).thenReturn(200L);
        Meetup meetup2 = mock(Meetup.class);
        when(meetup2.getIdx()).thenReturn(2L);
        when(meetup2.getOrganizer()).thenReturn(organizer2);
        when(meetup2.getTitle()).thenReturn("모임2");

        when(meetupRepository.findWithoutChatRoom()).thenReturn(List.of(meetup1, meetup2));
        doThrow(new RuntimeException("첫 번째 복구 실패"))
                .when(meetupChatRoomCreationService).createChatRoom(1L, 100L, "모임1");

        scheduler.recoverMissingChatRooms();

        verify(meetupChatRoomCreationService).createChatRoom(2L, 200L, "모임2");
    }
}
