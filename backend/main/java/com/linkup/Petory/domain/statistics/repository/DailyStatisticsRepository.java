package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;

@Repository
public interface DailyStatisticsRepository extends JpaRepository<DailyStatistics, Long> {

    Optional<DailyStatistics> findByStatDate(LocalDate statDate);

    List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
}
