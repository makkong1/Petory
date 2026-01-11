package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;

/**
 * DailyStatistics 도메인 Repository 인터페이스입니다.
 */
public interface DailyStatisticsRepository {

    // 기본 CRUD 메서드
    DailyStatistics save(DailyStatistics dailyStatistics);

    Optional<DailyStatistics> findById(Long id);

    void delete(DailyStatistics dailyStatistics);

    void deleteById(Long id);

    /**
     * 통계 날짜로 조회
     */
    Optional<DailyStatistics> findByStatDate(LocalDate statDate);

    /**
     * 날짜 범위로 통계 조회
     */
    List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
}
