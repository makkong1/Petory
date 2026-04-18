package com.linkup.Petory.domain.statistics.service;

import com.linkup.Petory.domain.statistics.dto.DailyStatisticsResponse;
import com.linkup.Petory.domain.statistics.dto.MonthlyStatisticsResponse;
import com.linkup.Petory.domain.statistics.dto.TodaySnapshotResponse;
import com.linkup.Petory.domain.statistics.dto.WeeklyStatisticsResponse;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final WeeklyStatisticsRepository weeklyStatisticsRepository;
    private final MonthlyStatisticsRepository monthlyStatisticsRepository;
    private final StatisticsScheduler statisticsScheduler;

    public List<DailyStatisticsResponse> getDailyStatistics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate가 endDate보다 클 수 없습니다.");
        }
        return dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(startDate, endDate)
                .stream().map(DailyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    public List<WeeklyStatisticsResponse> getWeeklyStatistics(int year) {
        return weeklyStatisticsRepository.findByYearOrderByWeekNumberAsc(year)
                .stream().map(WeeklyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    public List<MonthlyStatisticsResponse> getMonthlyStatistics(int year) {
        return monthlyStatisticsRepository.findByYearOrderByMonthAsc(year)
                .stream().map(MonthlyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "todayStats", key = "'today'")
    public TodaySnapshotResponse getTodaySnapshot() {
        LocalDate today = LocalDate.now();
        DailyStatistics snapshot = dailyStatisticsRepository.findByStatDate(today)
                .orElse(DailyStatistics.builder().statDate(today).build());
        return TodaySnapshotResponse.from(snapshot);
    }

    @Transactional
    public void recordPayment(BigDecimal amount) {
        LocalDate today = LocalDate.now();
        DailyStatistics stats = dailyStatisticsRepository.findByStatDate(today)
                .orElse(DailyStatistics.builder().statDate(today).build());

        BigDecimal newRevenue = stats.getTotalRevenue().add(amount);
        long newCount = stats.getTransactionCount() + 1;
        BigDecimal newAvg = newRevenue.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        stats.setTotalRevenue(newRevenue);
        stats.setTransactionCount(newCount);
        stats.setAvgTransaction(newAvg);
        dailyStatisticsRepository.save(stats);
    }

    @Transactional
    public void backfill(LocalDate startDate, LocalDate endDate) {
        statisticsScheduler.backfill(startDate, endDate);
    }
}
