package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaDailyStatisticsRepository extends JpaRepository<DailyStatistics, Long> {

    Optional<DailyStatistics> findByStatDate(LocalDate statDate);

    List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
}

