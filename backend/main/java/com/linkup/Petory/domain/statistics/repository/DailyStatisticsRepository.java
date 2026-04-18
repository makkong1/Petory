package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatisticsRepository {
    DailyStatistics save(DailyStatistics dailyStatistics);
    Optional<DailyStatistics> findById(Long id);
    void delete(DailyStatistics dailyStatistics);
    void deleteById(Long id);
    Optional<DailyStatistics> findByStatDate(LocalDate statDate);
    List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
    List<LocalDate> findStatDatesByDateRange(LocalDate startDate, LocalDate endDate);
    void deleteByStatDateBefore(LocalDate cutoffDate);
}
