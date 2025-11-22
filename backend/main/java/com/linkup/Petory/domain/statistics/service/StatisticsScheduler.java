package com.linkup.Petory.domain.statistics.service;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsScheduler {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;

    /**
     * 매일 오후 6시 30분(18:30:00)에 실행되어 '어제'의 통계를 집계
     */
    @Scheduled(cron = "0 30 18 * * ?")
    @Transactional
    public void aggregateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        aggregateStatisticsForDate(yesterday);
    }

    /**
     * 특정 날짜의 통계 집계 (재사용 가능)
     */
    @Transactional
    public void aggregateStatisticsForDate(LocalDate date) {
        log.info("일일 통계 집계 시작: {}", date);

        // 이미 집계된 데이터가 있는지 확인 (중복 방지)
        if (dailyStatisticsRepository.findByStatDate(date).isPresent()) {
            log.warn("이미 {}의 통계가 존재합니다. 집계를 건너뜁니다.", date);
            return;
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 1. 신규 가입자
        long newUsers = usersRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 2. 새 게시글
        long newPosts = boardRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 3. 케어 요청
        long newCareRequests = careRequestRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 4. 완료된 케어
        long completedCares = careRequestRepository.countByDateBetweenAndStatus(startOfDay, endOfDay,
                CareRequestStatus.COMPLETED);

        // 5. 매출 (현재 0으로 고정)
        BigDecimal totalRevenue = BigDecimal.ZERO;

        // 6. DAU
        long activeUsers = usersRepository.countByLastLoginAtBetween(startOfDay, endOfDay);

        DailyStatistics stats = DailyStatistics.builder()
                .statDate(date)
                .newUsers((int) newUsers)
                .newPosts((int) newPosts)
                .newCareRequests((int) newCareRequests)
                .completedCares((int) completedCares)
                .totalRevenue(totalRevenue)
                .activeUsers((int) activeUsers)
                .build();

        dailyStatisticsRepository.save(stats);

        log.info("일일 통계 집계 완료: {}", stats);
    }

    /**
     * 과거 데이터 초기화 (Backfill)
     * 오늘 기준 days일 전부터 어제까지 집계
     */
    @Transactional
    public void backfillStatistics(int days) {
        LocalDate today = LocalDate.now();
        for (int i = days; i > 0; i--) {
            LocalDate targetDate = today.minusDays(i);
            aggregateStatisticsForDate(targetDate);
        }
    }
}
