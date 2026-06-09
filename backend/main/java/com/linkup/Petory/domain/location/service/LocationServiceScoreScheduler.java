package com.linkup.Petory.domain.location.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.entity.LocationService;
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
        List<LocationService> all = locationServiceRepository.findAll();
        for (LocationService ls : all) {
            ls.setScore(computeScore(ls));
        }
        locationServiceRepository.saveAll(all);
        log.info("[ScoreScheduler] score 재계산 완료: {}건", all.size());
    }

    private double computeScore(LocationService ls) {
        double ratingScore = (ls.getRating() != null ? ls.getRating() : 0.0)
                * Math.log10((ls.getReviewCount() != null ? ls.getReviewCount() : 0) + 1);
        double petBonus = Boolean.TRUE.equals(ls.getPetFriendly()) ? 1.0 : 0.0;
        return 0.5 * ratingScore + 0.2 * petBonus;
    }
}
