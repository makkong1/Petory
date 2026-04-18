package com.linkup.Petory.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@SpringBootTest
class StatisticsRepositoryQueryTest {

    @Autowired CareRequestRepository careRequestRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired UsersRepository usersRepository;

    @Test
    void countCancelledCares_shouldReturnLong() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        long count = careRequestRepository.countByStatusAndUpdatedAtBetween(
                CareRequestStatus.CANCELLED, start, end);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countResolvedReports_shouldReturnLong() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        long count = reportRepository.countByStatusAndUpdatedAtBetween(
                ReportStatus.RESOLVED, start, end);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countNewProviders_shouldReturnLong() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        long count = usersRepository.countByRoleAndCreatedAtBetween(
                Role.SERVICE_PROVIDER, start, end);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
