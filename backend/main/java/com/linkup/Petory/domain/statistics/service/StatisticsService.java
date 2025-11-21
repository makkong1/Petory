package com.linkup.Petory.domain.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final StatisticsScheduler statisticsScheduler;

    /**
     * 과거 통계 초기화 (Backfill)
     */
    @Transactional
    public void initStatistics(int days) {
        statisticsScheduler.backfillStatistics(days);
    }

    /**
     * 기간별 일일 통계 조회 (오늘 날짜 포함 시 실시간 집계 추가)
     */
    public List<DailyStatistics> getDailyStatistics(LocalDate startDate, LocalDate endDate) {
        List<DailyStatistics> stats = dailyStatisticsRepository.findByStatDateBetweenOrderByStatDateAsc(startDate,
                endDate);

        LocalDate today = LocalDate.now();
        // 조회 기간에 오늘이 포함되어 있고, DB에 아직 오늘의 통계가 없는 경우 (보통 스케줄러는 내일 도니까 없음)
        if (!startDate.isAfter(today) && !endDate.isBefore(today)) {
            boolean todayExists = stats.stream().anyMatch(s -> s.getStatDate().equals(today));
            if (!todayExists) {
                stats.add(calculateTodayStatistics());
            }
        }

        return stats;
    }

    /**
     * 특정 날짜의 통계 조회 (없으면 null)
     */
    public DailyStatistics getDailyStatistics(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            return calculateTodayStatistics();
        }
        return dailyStatisticsRepository.findByStatDate(date).orElse(null);
    }

    private DailyStatistics calculateTodayStatistics() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = LocalDateTime.now(); // 현재 시점까지

        long newUsers = usersRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long newPosts = boardRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long newCareRequests = careRequestRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long completedCares = careRequestRepository.countByDateBetweenAndStatus(startOfDay, endOfDay,
                CareRequestStatus.COMPLETED);
        long activeUsers = usersRepository.countByLastLoginAtBetween(startOfDay, endOfDay);

        return DailyStatistics.builder()
                .statDate(today)
                .newUsers((int) newUsers)
                .newPosts((int) newPosts)
                .newCareRequests((int) newCareRequests)
                .completedCares((int) completedCares)
                .totalRevenue(BigDecimal.ZERO)
                .activeUsers((int) activeUsers)
                .build();
    }
}
