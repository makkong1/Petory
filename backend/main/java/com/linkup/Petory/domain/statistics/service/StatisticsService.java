package com.linkup.Petory.domain.statistics.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.statistics.dto.DailyStatisticsResponse;
import com.linkup.Petory.domain.statistics.dto.MonthlyStatisticsResponse;
import com.linkup.Petory.domain.statistics.dto.TodaySnapshotResponse;
import com.linkup.Petory.domain.statistics.dto.WeeklyStatisticsResponse;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * 통계 조회 및 결제 이벤트 즉시 반영을 담당하는 서비스. 집계 작업은 StatisticsScheduler에 위임한다.
 */
public class StatisticsService {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final WeeklyStatisticsRepository weeklyStatisticsRepository;
    private final MonthlyStatisticsRepository monthlyStatisticsRepository;
    private final StatisticsScheduler statisticsScheduler;

    /**
     * 날짜 범위 내 일별 통계를 오름차순으로 반환한다.
     */
    public List<DailyStatisticsResponse> getDailyStatistics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate가 endDate보다 클 수 없습니다.");
        }
        return dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(startDate, endDate)
                .stream().map(DailyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 연도의 주간 통계 목록을 주차 오름차순으로 반환한다.
     */
    public List<WeeklyStatisticsResponse> getWeeklyStatistics(int year) {
        return weeklyStatisticsRepository.findByYearOrderByWeekNumberAsc(year)
                .stream().map(WeeklyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 연도의 월간 통계 목록을 월 오름차순으로 반환한다.
     */
    public List<MonthlyStatisticsResponse> getMonthlyStatistics(int year) {
        return monthlyStatisticsRepository.findByYearOrderByMonthAsc(year)
                .stream().map(MonthlyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 오늘 통계 스냅샷을 반환한다. 집계 레코드가 없으면 빈 값으로 응답한다. Redis에 캐싱된다.
     */
    @Cacheable(value = "todayStats", key = "'today'")
    public TodaySnapshotResponse getTodaySnapshot() {
        LocalDate today = LocalDate.now();
        DailyStatistics snapshot = dailyStatisticsRepository.findByStatDate(today)
                .orElse(DailyStatistics.builder().statDate(today).build());
        return TodaySnapshotResponse.from(snapshot);
    }

    /**
     * 결제 발생 시 오늘 통계에 매출·건수·평균 거래액을 즉시 반영한다.
     */
    @Transactional
    @CacheEvict(value = "todayStats", key = "'today'")
    public void recordPayment(BigDecimal amount) {
        dailyStatisticsRepository.upsertPayment(LocalDate.now(), amount);
    }

    /**
     * 누락된 날짜 범위의 통계를 소급 집계한다.
     */
    @Transactional
    public void backfill(LocalDate startDate, LocalDate endDate) {
        statisticsScheduler.backfill(startDate, endDate);
    }
}
