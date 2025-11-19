package com.linkup.Petory.domain.statistics.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticsRepository dailyStatisticsRepository;

    /**
     * 기간별 일일 통계 조회
     */
    public List<DailyStatistics> getDailyStatistics(LocalDate startDate, LocalDate endDate) {
        return dailyStatisticsRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate);
    }

    /**
     * 특정 날짜의 통계 조회 (없으면 null)
     */
    public DailyStatistics getDailyStatistics(LocalDate date) {
        return dailyStatisticsRepository.findByStatDate(date).orElse(null);
    }
}
