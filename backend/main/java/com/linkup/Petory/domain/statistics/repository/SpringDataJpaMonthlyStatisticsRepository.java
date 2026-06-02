package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** MonthlyStatistics Spring Data JPA 전용 인터페이스. */
public interface SpringDataJpaMonthlyStatisticsRepository extends JpaRepository<MonthlyStatistics, Long> {
    Optional<MonthlyStatistics> findByYearAndMonth(int year, int month);
    List<MonthlyStatistics> findByYearOrderByMonthAsc(int year);
    Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc();
}
