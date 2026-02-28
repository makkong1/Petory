package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}

