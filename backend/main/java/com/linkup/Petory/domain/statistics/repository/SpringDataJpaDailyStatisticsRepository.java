package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaDailyStatisticsRepository extends JpaRepository<DailyStatistics, Long> {

    @RepositoryMethod("일별 통계: 날짜로 조회")
    Optional<DailyStatistics> findByStatDate(LocalDate statDate);

    @RepositoryMethod("일별 통계: 날짜 범위별 조회")
    List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT d.statDate FROM DailyStatistics d WHERE d.statDate BETWEEN :startDate AND :endDate")
    List<LocalDate> findStatDatesByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    void deleteByStatDateBefore(LocalDate cutoffDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailyStatistics d WHERE d.statDate = :date")
    @RepositoryMethod("일별 통계: 날짜로 조회 (비관적 락)")
    Optional<DailyStatistics> findByStatDateForUpdate(@Param("date") LocalDate date);
}
