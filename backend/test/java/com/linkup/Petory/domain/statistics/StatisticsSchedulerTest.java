package com.linkup.Petory.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.linkup.Petory.domain.statistics.service.StatisticsScheduler;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class StatisticsSchedulerTest {

    @Mock DailyStatisticsRepository dailyStatisticsRepository;
    @Mock WeeklyStatisticsRepository weeklyStatisticsRepository;
    @Mock MonthlyStatisticsRepository monthlyStatisticsRepository;
    @Mock UsersRepository usersRepository;
    @Mock BoardRepository boardRepository;
    @Mock CareRequestRepository careRequestRepository;
    @Mock MeetupRepository meetupRepository;
    @Mock MeetupParticipantsRepository meetupParticipantsRepository;
    @Mock ReportRepository reportRepository;

    @InjectMocks StatisticsScheduler scheduler;

    @BeforeEach
    void setup() {
        lenient().when(usersRepository.countByCreatedAtBetween(any(), any())).thenReturn(5L);
        lenient().when(usersRepository.countByLastLoginAtBetween(any(), any())).thenReturn(100L);
        lenient().when(usersRepository.countByRoleAndCreatedAtBetween(eq(Role.SERVICE_PROVIDER), any(), any())).thenReturn(2L);
        lenient().when(boardRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);
        lenient().when(careRequestRepository.countByCreatedAtBetween(any(), any())).thenReturn(8L);
        lenient().when(careRequestRepository.countByCompletedAtBetween(any(), any())).thenReturn(6L);
        lenient().when(careRequestRepository.countByStatusAndUpdatedAtBetween(eq(CareRequestStatus.CANCELLED), any(), any())).thenReturn(1L);
        lenient().when(meetupRepository.countByCreatedAtBetween(any(), any())).thenReturn(3L);
        lenient().when(meetupParticipantsRepository.countByJoinedAtBetween(any(), any())).thenReturn(15L);
        lenient().when(reportRepository.countByCreatedAtBetween(any(), any())).thenReturn(2L);
        lenient().when(reportRepository.countByStatusAndUpdatedAtBetween(eq(ReportStatus.RESOLVED), any(), any())).thenReturn(1L);
        lenient().when(dailyStatisticsRepository.findByStatDate(any())).thenReturn(Optional.empty());
        lenient().when(dailyStatisticsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aggregateForDate_savesAllFields() {
        LocalDate date = LocalDate.of(2026, 4, 17);
        scheduler.aggregateStatisticsForDate(date);
        verify(dailyStatisticsRepository).save(argThat(s ->
                s.getNewUsers() == 5L &&
                s.getCompletedCares() == 6L &&
                s.getCancelledCares() == 1L &&
                s.getNewProviders() == 2L &&
                s.getResolvedReports() == 1L
        ));
    }

    @Test
    void aggregateForDate_skipIfAlreadyExists() {
        LocalDate date = LocalDate.of(2026, 4, 17);
        when(dailyStatisticsRepository.findByStatDate(date)).thenReturn(Optional.of(new DailyStatistics()));
        scheduler.aggregateStatisticsForDate(date);
        verify(dailyStatisticsRepository, never()).save(any());
    }

    @Test
    void careCompletionRate_calculatedCorrectly() {
        LocalDate date = LocalDate.of(2026, 4, 17);
        scheduler.aggregateStatisticsForDate(date);
        verify(dailyStatisticsRepository).save(argThat(s ->
                s.getCareCompletionRate().compareTo(new BigDecimal("85.71")) == 0
        ));
    }
}
