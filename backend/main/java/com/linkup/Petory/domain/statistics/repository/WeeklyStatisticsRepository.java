package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import java.util.List;
import java.util.Optional;

public interface WeeklyStatisticsRepository {
    WeeklyStatistics save(WeeklyStatistics weeklyStatistics);
    Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber);
    List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year);
    List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(int startYear, int startWeek, int endYear, int endWeek);
}
