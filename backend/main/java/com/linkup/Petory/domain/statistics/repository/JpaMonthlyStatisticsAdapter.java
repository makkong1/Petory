package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository @Primary @RequiredArgsConstructor
public class JpaMonthlyStatisticsAdapter implements MonthlyStatisticsRepository {
    private final SpringDataJpaMonthlyStatisticsRepository jpaRepository;

    @Override
    public MonthlyStatistics save(MonthlyStatistics m) { return jpaRepository.save(m); }

    @Override
    public Optional<MonthlyStatistics> findByYearAndMonth(int year, int month) {
        return jpaRepository.findByYearAndMonth(year, month);
    }

    @Override
    public List<MonthlyStatistics> findByYearOrderByMonthAsc(int year) {
        return jpaRepository.findByYearOrderByMonthAsc(year);
    }

    @Override
    public Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc() {
        return jpaRepository.findTopByOrderByYearDescMonthDesc();
    }
}
