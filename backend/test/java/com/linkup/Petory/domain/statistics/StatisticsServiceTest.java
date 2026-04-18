package com.linkup.Petory.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.statistics.dto.DailyStatisticsResponse;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import com.linkup.Petory.domain.statistics.service.StatisticsScheduler;
import com.linkup.Petory.domain.statistics.service.StatisticsService;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock DailyStatisticsRepository dailyStatisticsRepository;
    @Mock WeeklyStatisticsRepository weeklyStatisticsRepository;
    @Mock MonthlyStatisticsRepository monthlyStatisticsRepository;
    @Mock StatisticsScheduler statisticsScheduler;

    @InjectMocks StatisticsService statisticsService;

    @Test
    void getDailyStatistics_returnsMappedResponse() {
        DailyStatistics entity = DailyStatistics.builder()
                .statDate(LocalDate.of(2026, 4, 17))
                .newUsers(10L).activeUsers(200L).newProviders(2L)
                .newCareRequests(5L).completedCares(4L).cancelledCares(1L)
                .careCompletionRate(new BigDecimal("80.00"))
                .totalRevenue(new BigDecimal("50000")).transactionCount(4L)
                .avgTransaction(new BigDecimal("12500"))
                .newPosts(20L).newMeetups(3L).meetupParticipants(12L)
                .newReports(1L).resolvedReports(1L)
                .build();
        when(dailyStatisticsRepository.findByStatDateBetweenOrderByStatDateAsc(any(), any()))
                .thenReturn(List.of(entity));

        List<DailyStatisticsResponse> result = statisticsService.getDailyStatistics(
                LocalDate.of(2026, 4, 17), LocalDate.of(2026, 4, 17));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsers().getNewUsers()).isEqualTo(10L);
        assertThat(result.get(0).getCare().getCompletionRate()).isEqualTo(new BigDecimal("80.00"));
        assertThat(result.get(0).getRevenue().getTotalRevenue()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    void recordPayment_updatesRevenue() {
        DailyStatistics today = DailyStatistics.builder()
                .statDate(LocalDate.now())
                .totalRevenue(BigDecimal.ZERO)
                .transactionCount(0L)
                .avgTransaction(BigDecimal.ZERO)
                .build();
        when(dailyStatisticsRepository.findByStatDate(LocalDate.now())).thenReturn(Optional.of(today));
        when(dailyStatisticsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        statisticsService.recordPayment(new BigDecimal("30000"));

        assertThat(today.getTotalRevenue()).isEqualTo(new BigDecimal("30000"));
        assertThat(today.getTransactionCount()).isEqualTo(1L);
        assertThat(today.getAvgTransaction()).isEqualTo(new BigDecimal("30000.00"));
    }

    @Test
    void getDailyStatistics_throwsWhenStartAfterEnd() {
        assertThatThrownBy(() -> statisticsService.getDailyStatistics(
                LocalDate.of(2026, 4, 18), LocalDate.of(2026, 4, 17)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
