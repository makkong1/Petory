package com.linkup.Petory.domain.location.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.repository.SpringDataJpaLocationServiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationServiceScoreScheduler {

    private final SpringDataJpaLocationServiceRepository locationServiceRepository;

    /**
     * 매일 자정 전체 score 재계산. score = 0.5 × rating × log10(reviewCount+1) + 0.2 ×
     * petFriendly
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void recalculateAllScores() {
        log.info("[ScoreScheduler] location service score 재계산 시작");
        int updated = locationServiceRepository.recalculateScores();
        log.info("[ScoreScheduler] score 재계산 완료: {}건 갱신", updated);
    }
}
