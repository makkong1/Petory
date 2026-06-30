package com.linkup.Petory.domain.statistics.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;

/**
 * 주간 통계 저장소 인터페이스. JPA 구현체는 JpaWeeklyStatisticsAdapter.
 */
public interface WeeklyStatisticsRepository {

    WeeklyStatistics save(WeeklyStatistics weeklyStatistics);

    Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber);

    List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year);

    List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(int startYear, int startWeek, int endYear, int endWeek);
}
