package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.event.MeetupCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 모임 생성 이벤트 리스너
 * 모임 생성 완료 후 채팅방을 생성하는 후처리 작업을 담당
 * 
 * 설계 원칙:
 * - 모임 생성은 핵심 도메인 (단독 트랜잭션)
 * - 채팅방 생성은 파생 도메인 (후처리, 별도 트랜잭션)
 * - 채팅방 생성 실패가 모임 생성까지 롤백하지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetupChatRoomEventListener {

        private final MeetupChatRoomCreationService meetupChatRoomCreationService;

        @EventListener
        @Async
        public void handleMeetupCreated(MeetupCreatedEvent event) {
                log.info("모임 채팅방 생성 이벤트 수신: meetupIdx={}", event.getMeetupIdx());
                meetupChatRoomCreationService.createChatRoom(
                                event.getMeetupIdx(), event.getOrganizerIdx(), event.getMeetupTitle());
        }
}
