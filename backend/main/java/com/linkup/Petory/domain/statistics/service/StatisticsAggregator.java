package com.linkup.Petory.domain.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 날짜별 일일 통계 집계 담당 빈. StatisticsScheduler에서 self-invocation 없이 호출된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsAggregator {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public void aggregateForDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        DailyStatistics stats = dailyStatisticsRepository.findByStatDate(date)
                .orElse(DailyStatistics.builder().statDate(date).build());

        boolean hasPaymentData = stats.getTransactionCount() != null && stats.getTransactionCount() > 0;

        long completed = careRequestRepository.countByCompletedAtBetween(start, end);
        long cancelled = careRequestRepository.countByStatusAndUpdatedAtBetween(CareRequestStatus.CANCELLED, start, end);

        stats.setNewUsers(usersRepository.countByCreatedAtBetween(start, end));
        stats.setActiveUsers(usersRepository.countByLastLoginAtBetween(start, end));
        stats.setNewProviders(usersRepository.countByRoleAndCreatedAtBetween(Role.SERVICE_PROVIDER, start, end));
        stats.setNewCareRequests(careRequestRepository.countByCreatedAtBetween(start, end));
        stats.setCompletedCares(completed);
        stats.setCancelledCares(cancelled);
        stats.setCareCompletionRate(calcRate(completed, completed + cancelled));
        stats.setNewPosts(boardRepository.countByCreatedAtBetween(start, end));
        stats.setNewMeetups(meetupRepository.countByCreatedAtBetween(start, end));
        stats.setMeetupParticipants(meetupParticipantsRepository.countByJoinedAtBetween(start, end));
        stats.setNewReports(reportRepository.countByCreatedAtBetween(start, end));
        stats.setResolvedReports(reportRepository.countByStatusAndUpdatedAtBetween(ReportStatus.RESOLVED, start, end));

        if (!hasPaymentData) {
            stats.setTotalRevenue(BigDecimal.ZERO);
            stats.setTransactionCount(0L);
            stats.setAvgTransaction(BigDecimal.ZERO);
        }

        dailyStatisticsRepository.save(stats);
        log.info("일일 통계 집계 완료 (merge): {}", date);
    }

    private BigDecimal calcRate(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }
}
