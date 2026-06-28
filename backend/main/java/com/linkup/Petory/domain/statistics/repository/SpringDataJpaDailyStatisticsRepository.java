package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying
    @RepositoryMethod("일별 통계: 결제 집계 upsert")
    @Query(value = """
            INSERT INTO dailystatistics (stat_date, total_revenue, transaction_count, avg_transaction)
            VALUES (:date, :amount, 1, :amount)
            ON DUPLICATE KEY UPDATE
                avg_transaction = (COALESCE(total_revenue, 0) + :amount) / (transaction_count + 1),
                total_revenue = COALESCE(total_revenue, 0) + :amount,
                transaction_count = transaction_count + 1
            """, nativeQuery = true)
    void upsertPayment(@Param("date") LocalDate date, @Param("amount") java.math.BigDecimal amount);
}
