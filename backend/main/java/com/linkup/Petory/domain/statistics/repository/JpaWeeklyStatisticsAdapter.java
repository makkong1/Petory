package com.linkup.Petory.domain.statistics.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;

import lombok.RequiredArgsConstructor;

@Repository
@Primary
@RequiredArgsConstructor
/** WeeklyStatisticsRepository의 JPA 구현체(어댑터). */
public class JpaWeeklyStatisticsAdapter implements WeeklyStatisticsRepository {
    private final SpringDataJpaWeeklyStatisticsRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public WeeklyStatistics save(WeeklyStatistics w) {
        return jpaRepository.save(w);
    }

    @Override
    public Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber) {
        return jpaRepository.findByYearAndWeekNumber(year, weekNumber);
    }

    @Override
    public List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year) {
        return jpaRepository.findByYearOrderByWeekNumberAsc(year);
    }

    @Override
    public List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(int startYear, int startWeek, int endYear,
            int endWeek) {
        return jpaRepository.findByYearBetweenAndWeekNumberBetween(startYear, startWeek, endYear, endWeek);
    }
}
