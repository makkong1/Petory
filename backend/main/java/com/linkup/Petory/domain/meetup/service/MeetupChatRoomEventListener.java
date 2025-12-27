package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.service.ConversationService;
import com.linkup.Petory.domain.meetup.event.MeetupCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        private final ConversationService conversationService;

        /**
         * 이 이벤트 리스너는 모임 생성 완료 이벤트를 수신하여 채팅방을 생성
         * 별도 트랜잭션으로 처리하여 모임 생성 트랜잭션과 분리
         * 
         * @Async: 비동기 처리로 모임 생성 응답 속도 향상
         *         @Transactional(REQUIRES_NEW): 별도 트랜잭션으로 처리하여 모임 생성 트랜잭션과 분리
         */
        @EventListener
        @Async
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void handleMeetupCreated(MeetupCreatedEvent event) {
                Long meetupIdx = event.getMeetupIdx();
                Long organizerIdx = event.getOrganizerIdx();
                String meetupTitle = event.getMeetupTitle();

                log.info("모임 채팅방 생성 시작: meetupIdx={}, organizerIdx={}, title={}",
                                meetupIdx, organizerIdx, meetupTitle);

                try {
                        // 그룹 채팅방 생성 (주최자만 초기 참여)
                        conversationService.createConversation(
                                        ConversationType.MEETUP,
                                        RelatedType.MEETUP,
                                        meetupIdx,
                                        meetupTitle,
                                        List.of(organizerIdx));

                        // 주최자를 ADMIN 역할로 설정
                        conversationService.setParticipantRole(
                                        RelatedType.MEETUP,
                                        meetupIdx,
                                        organizerIdx,
                                        ParticipantRole.ADMIN);

                        log.info("모임 채팅방 생성 완료: meetupIdx={}, organizerIdx={}",
                                        meetupIdx, organizerIdx);

                } catch (Exception e) {
                        // 채팅방 생성 실패해도 모임은 이미 생성됨 (롤백되지 않음)
                        log.error("모임 채팅방 생성 실패: meetupIdx={}, organizerIdx={}, error={}",
                                        meetupIdx, organizerIdx, e.getMessage(), e);

                        // TODO: 재시도 메커니즘 추가 고려
                        // - 스케줄러로 채팅방 없는 모임 감지 및 재시도
                        // - 알림 발송 (관리자에게)
                }
        }
}
