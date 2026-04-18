package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import java.util.List;
import java.util.Optional;

public interface MonthlyStatisticsRepository {
    MonthlyStatistics save(MonthlyStatistics monthlyStatistics);
    Optional<MonthlyStatistics> findByYearAndMonth(int year, int month);
    List<MonthlyStatistics> findByYearOrderByMonthAsc(int year);
    Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc();
}
