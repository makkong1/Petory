package com.linkup.Petory.domain.meetup.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.meetup.repository.MeetupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모임 상태 자동 전이 (정원 마감 → CLOSED, 일시 경과 → COMPLETED).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetupScheduler {

    private final MeetupRepository meetupRepository;

    /**
     * 매시 정각 실행 (케어 요청 스케줄러와 동일한 주기).
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void transitionMeetupStatuses() {
        LocalDateTime now = LocalDateTime.now();
        int closed = meetupRepository.closeFullRecruitingMeetups(now);
        int completed = meetupRepository.completePastMeetups(now);
        if (closed > 0 || completed > 0) {
            log.info("모임 상태 자동 전이: CLOSED={}, COMPLETED={}", closed, completed);
        }
    }
}
