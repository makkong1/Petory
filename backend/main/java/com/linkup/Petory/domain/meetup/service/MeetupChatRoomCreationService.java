package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.service.ConversationCreatorService;
import com.linkup.Petory.domain.chat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetupChatRoomCreationService {

    private final ConversationCreatorService conversationCreatorService;
    private final ConversationService conversationService;

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void createChatRoom(Long meetupIdx, Long organizerIdx, String meetupTitle) {
        conversationCreatorService.createConversation(
                ConversationType.MEETUP,
                RelatedType.MEETUP,
                meetupIdx,
                meetupTitle,
                List.of(organizerIdx),
                organizerIdx);

        conversationService.setParticipantRole(
                RelatedType.MEETUP,
                meetupIdx,
                organizerIdx,
                ParticipantRole.ADMIN);

        log.info("모임 채팅방 생성 완료: meetupIdx={}, organizerIdx={}", meetupIdx, organizerIdx);
    }

    @Recover
    public void recover(Exception e, Long meetupIdx, Long organizerIdx, String meetupTitle) {
        log.error("모임 채팅방 생성 최종 실패 (3회 재시도 소진): meetupIdx={}, organizerIdx={}, error={}",
                meetupIdx, organizerIdx, e.getMessage(), e);
    }
}
