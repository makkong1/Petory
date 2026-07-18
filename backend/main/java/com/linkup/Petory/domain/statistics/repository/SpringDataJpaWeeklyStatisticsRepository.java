package com.linkup.Petory.domain.statistics.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;

/**
 * WeeklyStatistics Spring Data JPA 전용 인터페이스.
 */
public interface SpringDataJpaWeeklyStatisticsRepository extends JpaRepository<WeeklyStatistics, Long> {

    Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber);

    List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year);
}
