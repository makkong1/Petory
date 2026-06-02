package com.linkup.Petory.domain.statistics.service;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 통계 집계 스케줄러. 매일 18:00에 전날 일별 통계를 집계하고, 일요일·월말에 주간·월간 롤업을 실행한다.
 * 누락된 날짜는 자동으로 감지해 backfill한다.
 */
public class StatisticsScheduler {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final WeeklyStatisticsRepository weeklyStatisticsRepository;
    private final MonthlyStatisticsRepository monthlyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final ReportRepository reportRepository;

    /** 매일 18:00 실행. 전날 통계 집계 → 일요일이면 주간 롤업 → 월말이면 월간 롤업 → 1년 초과 데이터 삭제. */
    @Scheduled(cron = "0 0 18 * * ?")
    @Transactional
    public void aggregateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            detectAndBackfillMissing(yesterday);
            aggregateStatisticsForDate(yesterday);
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

    /** 특정 날짜의 일별 통계를 집계해 저장한다. 이미 존재하면 건너뛴다. */
    @Transactional
    public void aggregateStatisticsForDate(LocalDate date) {
        if (dailyStatisticsRepository.findByStatDate(date).isPresent()) {
            log.warn("이미 {}의 통계가 존재합니다. 건너뜁니다.", date);
            return;
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        long completed = careRequestRepository.countByCompletedAtBetween(start, end);
        long cancelled = careRequestRepository.countByStatusAndUpdatedAtBetween(CareRequestStatus.CANCELLED, start, end);
        BigDecimal completionRate = calcRate(completed, completed + cancelled);

        DailyStatistics stats = DailyStatistics.builder()
                .statDate(date)
                .newUsers(usersRepository.countByCreatedAtBetween(start, end))
                .activeUsers(usersRepository.countByLastLoginAtBetween(start, end))
                .newProviders(usersRepository.countByRoleAndCreatedAtBetween(Role.SERVICE_PROVIDER, start, end))
                .newCareRequests(careRequestRepository.countByCreatedAtBetween(start, end))
                .completedCares(completed)
                .cancelledCares(cancelled)
                .careCompletionRate(completionRate)
                .totalRevenue(BigDecimal.ZERO)
                .transactionCount(0L)
                .avgTransaction(BigDecimal.ZERO)
                .newPosts(boardRepository.countByCreatedAtBetween(start, end))
                .newMeetups(meetupRepository.countByCreatedAtBetween(start, end))
                .meetupParticipants(meetupParticipantsRepository.countByJoinedAtBetween(start, end))
                .newReports(reportRepository.countByCreatedAtBetween(start, end))
                .resolvedReports(reportRepository.countByStatusAndUpdatedAtBetween(ReportStatus.RESOLVED, start, end))
                .build();

        dailyStatisticsRepository.save(stats);
        log.info("일일 통계 집계 완료: {}", date);
    }

    /** 해당 주(일요일 기준)의 일별 통계를 합산해 주간 통계를 생성한다. */
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

    /** 특정 연월의 일별 통계를 합산해 월간 통계를 생성한다. */
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

    /** 날짜 범위 내 누락된 일별 통계를 순서대로 소급 집계한다. */
    @Transactional
    public void backfill(LocalDate startDate, LocalDate endDate) {
        startDate.datesUntil(endDate.plusDays(1)).forEach(date -> {
            try {
                aggregateStatisticsForDate(date);
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
                    aggregateStatisticsForDate(date);
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
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcAvgTransaction(BigDecimal total, long count) {
        if (count == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcWeeklyRetention(int year, int weekNumber, long currentWau) {
        int prevWeek = weekNumber - 1;
        int prevYear = year;
        if (prevWeek == 0) { prevYear--; prevWeek = 52; }
        return weeklyStatisticsRepository.findByYearAndWeekNumber(prevYear, prevWeek)
                .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
                        : calcRate(currentWau, prev.getActiveUsers()))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calcMonthlyRetention(int year, int month, long currentMau) {
        int prevMonth = month - 1;
        int prevYear = year;
        if (prevMonth == 0) { prevYear--; prevMonth = 12; }
        return monthlyStatisticsRepository.findByYearAndMonth(prevYear, prevMonth)
                .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
                        : calcRate(currentMau, prev.getActiveUsers()))
                .orElse(BigDecimal.ZERO);
    }
}
