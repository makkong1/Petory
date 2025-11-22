package com.linkup.Petory.domain.board.service;

import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardPopularityScheduler {

    private final BoardPopularityService boardPopularityService;

    /**
     * 매일 오후 6시(18:00:00)에 실행되어 주간/월간 인기 게시글 스냅샷 생성
     */
    @Scheduled(cron = "0 0 18 * * ?")
    @Transactional
    public void generateDailyPopularitySnapshots() {
        log.info("인기 게시글 스냅샷 생성 시작 - {}", java.time.LocalDateTime.now());
        
        try {
            // 주간 스냅샷 생성
            log.info("주간 인기 게시글 스냅샷 생성 중...");
            boardPopularityService.generateSnapshots(PopularityPeriodType.WEEKLY);
            log.info("주간 인기 게시글 스냅샷 생성 완료");
            
            // 월간 스냅샷 생성
            log.info("월간 인기 게시글 스냅샷 생성 중...");
            boardPopularityService.generateSnapshots(PopularityPeriodType.MONTHLY);
            log.info("월간 인기 게시글 스냅샷 생성 완료");
            
            log.info("인기 게시글 스냅샷 생성 완료 - {}", java.time.LocalDateTime.now());
        } catch (Exception e) {
            log.error("인기 게시글 스냅샷 생성 중 오류 발생", e);
        }
    }
}

