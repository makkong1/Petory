package com.linkup.Petory.domain.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;

import java.time.temporal.IsoFields;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 통계 집계 스케줄러. 매일 00:05에 전날 일별 통계를 집계하고, 일요일·월말에 주간·월간 롤업을 실행한다. 누락된 날짜는 자동으로
 * 감지해 backfill한다.
 */
public class StatisticsScheduler {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final WeeklyStatisticsRepository weeklyStatisticsRepository;
    private final MonthlyStatisticsRepository monthlyStatisticsRepository;
    private final StatisticsAggregator statisticsAggregator;

    /**
     * 매일 00:05 실행. 전날 통계 집계 → 일요일이면 주간 롤업 → 월말이면 월간 롤업 → 1년 초과 데이터 삭제.
     * C0 임시 완화: 자정 직후 실행으로 DAU 집계 race window 최소화.
     */
    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional
    public void aggregateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            detectAndBackfillMissing(yesterday);
            statisticsAggregator.aggregateForDate(yesterday);
            if (yesterday.getDayOfWeek() == DayOfWeek.SUNDAY) {
                rollupWeekly(yesterday);
            }
            if (yesterday.equals(yesterday.withDayOfMonth(yesterday.lengthOfMonth()))) {
                rollupMonthly(yesterday.getYear(), yesterday.getMonthValue());
            }
            deleteExpiredDaily();
        } catch (Exception e) {
            log.error("통계 집계 실패 — 날짜: {}, 원인: {}", yesterday, e.getMessage(), e);
        }
    }

    /**
     * 해당 주(일요일 기준)의 일별 통계를 합산해 주간 통계를 생성한다.
     */
    @Transactional
    public void rollupWeekly(LocalDate sunday) {
        LocalDate monday = sunday.minusDays(6);
        int year = sunday.get(IsoFields.WEEK_BASED_YEAR);
        int weekNumber = sunday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        if (weeklyStatisticsRepository.findByYearAndWeekNumber(year, weekNumber).isPresent()) {
            log.warn("이미 {}년 {}주차 통계가 존재합니다.", year, weekNumber);
            return;
        }

        List<DailyStatistics> days = dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(monday, sunday);

        long completed = sumLong(days, DailyStatistics::getCompletedCares);
        long cancelled = sumLong(days, DailyStatistics::getCancelledCares);
        long currentWau = sumLong(days, DailyStatistics::getActiveUsers);
        BigDecimal retentionRate = calcWeeklyRetention(year, weekNumber, currentWau);
        BigDecimal totalRevenue = sumRevenue(days);
        long txCount = sumLong(days, DailyStatistics::getTransactionCount);

        WeeklyStatistics weekly = WeeklyStatistics.builder()
                .year(year).weekNumber(weekNumber)
                .startDate(monday).endDate(sunday)
                .newUsers(sumLong(days, DailyStatistics::getNewUsers))
                .activeUsers(currentWau)
                .newProviders(sumLong(days, DailyStatistics::getNewProviders))
                .weeklyRetentionRate(retentionRate)
                .newCareRequests(sumLong(days, DailyStatistics::getNewCareRequests))
                .completedCares(completed).cancelledCares(cancelled)
                .careCompletionRate(calcRate(completed, completed + cancelled))
                .totalRevenue(totalRevenue)
                .transactionCount(txCount)
                .avgTransaction(calcAvgTransaction(totalRevenue, txCount))
                .newPosts(sumLong(days, DailyStatistics::getNewPosts))
                .newMeetups(sumLong(days, DailyStatistics::getNewMeetups))
                .meetupParticipants(sumLong(days, DailyStatistics::getMeetupParticipants))
                .newReports(sumLong(days, DailyStatistics::getNewReports))
                .resolvedReports(sumLong(days, DailyStatistics::getResolvedReports))
                .build();

        weeklyStatisticsRepository.save(weekly);
        log.info("주간 통계 롤업 완료: {}년 {}주차", year, weekNumber);
    }

    /**
     * 특정 연월의 일별 통계를 합산해 월간 통계를 생성한다.
     */
    @Transactional
    public void rollupMonthly(int year, int month) {
        if (monthlyStatisticsRepository.findByYearAndMonth(year, month).isPresent()) {
            log.warn("이미 {}년 {}월 통계가 존재합니다.", year, month);
            return;
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<DailyStatistics> days = dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(start, end);

        long completed = sumLong(days, DailyStatistics::getCompletedCares);
        long cancelled = sumLong(days, DailyStatistics::getCancelledCares);
        long currentMau = sumLong(days, DailyStatistics::getActiveUsers);
        BigDecimal retention = calcMonthlyRetention(year, month, currentMau);
        BigDecimal churn = BigDecimal.valueOf(100).subtract(retention).max(BigDecimal.ZERO);
        BigDecimal totalRevenue = sumRevenue(days);
        long txCount = sumLong(days, DailyStatistics::getTransactionCount);

        MonthlyStatistics monthly = MonthlyStatistics.builder()
                .year(year).month(month)
                .newUsers(sumLong(days, DailyStatistics::getNewUsers))
                .activeUsers(currentMau)
                .newProviders(sumLong(days, DailyStatistics::getNewProviders))
                .monthlyRetentionRate(retention).churnRate(churn)
                .newCareRequests(sumLong(days, DailyStatistics::getNewCareRequests))
                .completedCares(completed).cancelledCares(cancelled)
                .careCompletionRate(calcRate(completed, completed + cancelled))
                .totalRevenue(totalRevenue)
                .transactionCount(txCount)
                .avgTransaction(calcAvgTransaction(totalRevenue, txCount))
                .newPosts(sumLong(days, DailyStatistics::getNewPosts))
                .newMeetups(sumLong(days, DailyStatistics::getNewMeetups))
                .meetupParticipants(sumLong(days, DailyStatistics::getMeetupParticipants))
                .newReports(sumLong(days, DailyStatistics::getNewReports))
                .resolvedReports(sumLong(days, DailyStatistics::getResolvedReports))
                .build();

        monthlyStatisticsRepository.save(monthly);
        log.info("월간 통계 롤업 완료: {}년 {}월", year, month);
    }

    /**
     * 날짜 범위 내 누락된 일별 통계를 순서대로 소급 집계한다.
     */
    @Transactional
    public void backfill(LocalDate startDate, LocalDate endDate) {
        startDate.datesUntil(endDate.plusDays(1)).forEach(date -> {
            try {
                statisticsAggregator.aggregateForDate(date);
            } catch (Exception e) {
                log.error("Backfill 실패 — 날짜: {}, 원인: {}", date, e.getMessage());
            }
        });
    }

    private void detectAndBackfillMissing(LocalDate yesterday) {
        LocalDate checkStart = yesterday.minusDays(6);
        Set<LocalDate> existing = dailyStatisticsRepository
                .findStatDatesByDateRange(checkStart, yesterday.minusDays(1))
                .stream().collect(Collectors.toSet());

        checkStart.datesUntil(yesterday).forEach(date -> {
            if (!existing.contains(date)) {
                log.warn("누락된 통계 감지: {} — 자동 backfill 실행", date);
                try {
                    statisticsAggregator.aggregateForDate(date);
                } catch (Exception e) {
                    log.error("자동 backfill 실패: {}", date);
                }
            }
        });
    }

    private void deleteExpiredDaily() {
        LocalDate cutoff = LocalDate.now().minusYears(1);
        dailyStatisticsRepository.deleteByStatDateBefore(cutoff);
    }

    private long sumLong(List<DailyStatistics> days, Function<DailyStatistics, Long> getter) {
        return days.stream().mapToLong(getter::apply).sum();
    }

    private BigDecimal sumRevenue(List<DailyStatistics> days) {
        return days.stream().map(DailyStatistics::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcRate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcAvgTransaction(BigDecimal total, long count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcWeeklyRetention(int year, int weekNumber, long currentWau) {
        int prevWeek = weekNumber - 1;
        int prevYear = year;
        if (prevWeek == 0) {
            prevYear--;
            // ISO 8601: 해당 연도 12월 28일의 주차 수가 그 해 최대 주차 (52 또는 53)
            prevWeek = LocalDate.of(prevYear, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        }
        return weeklyStatisticsRepository.findByYearAndWeekNumber(prevYear, prevWeek)
                .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
                : calcRate(currentWau, prev.getActiveUsers()))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calcMonthlyRetention(int year, int month, long currentMau) {
        int prevMonth = month - 1;
        int prevYear = year;
        if (prevMonth == 0) {
            prevYear--;
            prevMonth = 12;
        }
        return monthlyStatisticsRepository.findByYearAndMonth(prevYear, prevMonth)
                .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
                : calcRate(currentMau, prev.getActiveUsers()))
                .orElse(BigDecimal.ZERO);
    }
}
