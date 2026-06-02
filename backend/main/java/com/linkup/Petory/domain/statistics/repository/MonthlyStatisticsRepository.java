package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import java.util.List;
import java.util.Optional;

/** 월간 통계 저장소 인터페이스. JPA 구현체는 JpaMonthlyStatisticsAdapter. */
public interface MonthlyStatisticsRepository {
    MonthlyStatistics save(MonthlyStatistics monthlyStatistics);
    Optional<MonthlyStatistics> findByYearAndMonth(int year, int month);
    List<MonthlyStatistics> findByYearOrderByMonthAsc(int year);
    Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc();
}
