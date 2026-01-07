package com.linkup.Petory.domain.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;

import lombok.RequiredArgsConstructor;

/**
 * DailyStatisticsRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaDailyStatisticsAdapter implements DailyStatisticsRepository {

    private final SpringDataJpaDailyStatisticsRepository jpaRepository;

    @Override
    public DailyStatistics save(DailyStatistics dailyStatistics) {
        return jpaRepository.save(dailyStatistics);
    }

    @Override
    public Optional<DailyStatistics> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(DailyStatistics dailyStatistics) {
        jpaRepository.delete(dailyStatistics);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Optional<DailyStatistics> findByStatDate(LocalDate statDate) {
        return jpaRepository.findByStatDate(statDate);
    }

    @Override
    public List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate) {
        return jpaRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate);
    }
}

