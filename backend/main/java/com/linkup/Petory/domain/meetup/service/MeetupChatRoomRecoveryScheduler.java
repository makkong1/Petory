package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetupChatRoomRecoveryScheduler {

    private final MeetupRepository meetupRepository;
    private final MeetupChatRoomCreationService meetupChatRoomCreationService;

    @Scheduled(fixedDelay = 300_000)
    public void recoverMissingChatRooms() {
        List<Meetup> orphans = meetupRepository.findWithoutChatRoom();
        if (orphans.isEmpty()) return;

        log.warn("채팅방 없는 모임 감지: {}건 복구 시작", orphans.size());
        for (Meetup meetup : orphans) {
            try {
                meetupChatRoomCreationService.createChatRoom(
                        meetup.getIdx(),
                        meetup.getOrganizer().getIdx(),
                        meetup.getTitle());
            } catch (Exception e) {
                log.error("복구 실패: meetupIdx={}", meetup.getIdx(), e);
            }
        }
    }
}
