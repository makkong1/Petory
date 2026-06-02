package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** WeeklyStatistics Spring Data JPA 전용 인터페이스. */
public interface SpringDataJpaWeeklyStatisticsRepository extends JpaRepository<WeeklyStatistics, Long> {
    Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber);
    List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year);

    @Query("SELECT w FROM WeeklyStatistics w WHERE (w.year > :startYear OR (w.year = :startYear AND w.weekNumber >= :startWeek)) AND (w.year < :endYear OR (w.year = :endYear AND w.weekNumber <= :endWeek)) ORDER BY w.year ASC, w.weekNumber ASC")
    List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(
            @Param("startYear") int startYear, @Param("startWeek") int startWeek,
            @Param("endYear") int endYear, @Param("endWeek") int endWeek);
}
