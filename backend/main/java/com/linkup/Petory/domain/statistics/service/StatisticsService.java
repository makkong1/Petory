package com.linkup.Petory.domain.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.repository.ReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final ReportRepository reportRepository;
    private final StatisticsScheduler statisticsScheduler;
    // [FIX] self-proxy: @Cacheable이 self-call에서 동작하려면 Spring 프록시를 통해야 함
    private final ApplicationContext applicationContext;

    private StatisticsService getThis() {
        return applicationContext.getBean(StatisticsService.class);
    }

    /**
     * 과거 통계 초기화 (Backfill)
     */
    @Transactional
    public void initStatistics(int days) {
        statisticsScheduler.backfillStatistics(days);
    }

    /**
     * 기간별 일일 통계 조회 (오늘 날짜 포함 시 실시간 집계 추가)
     */
    public List<DailyStatistics> getDailyStatistics(LocalDate startDate, LocalDate endDate) {
        // [FIX] startDate > endDate 방어 처리
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate(" + startDate + ")가 endDate(" + endDate + ")보다 클 수 없습니다.");
        }

        List<DailyStatistics> stats = dailyStatisticsRepository.findByStatDateBetweenOrderByStatDateAsc(startDate,
                endDate);

        LocalDate today = LocalDate.now();
        if (!startDate.isAfter(today) && !endDate.isBefore(today)) {
            boolean todayExists = stats.stream().anyMatch(s -> s.getStatDate().equals(today));
            if (!todayExists) {
                // [FIX] self-proxy를 통해 @Cacheable이 적용된 메서드 호출
                stats.add(getThis().calculateTodayStatistics());
            }
        }

        return stats;
    }

    /**
     * 특정 날짜의 통계 조회 (없으면 null)
     */
    public DailyStatistics getDailyStatistics(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            // [FIX] self-proxy를 통해 @Cacheable이 적용된 메서드 호출
            return getThis().calculateTodayStatistics();
        }
        return dailyStatisticsRepository.findByStatDate(date).orElse(null);
    }

    /**
     * 오늘의 실시간 통계 계산 (1분 캐싱)
     * [FIX] @Cacheable(todayStats, 1min) — 관리자 대시보드 폴링 시 반복 쿼리 방지
     * 주의: self-invocation에서는 동작 안 함 — 반드시 getThis().calculateTodayStatistics()로 호출할 것
     */
    @Cacheable(value = "todayStats", key = "'today'")
    public DailyStatistics calculateTodayStatistics() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = LocalDateTime.now();

        long newUsers = usersRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long newPosts = boardRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long newCareRequests = careRequestRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        // [FIX] completedAt 기준 집계 — date(케어 예정일) 기준이면 완료 날짜와 무관한 결과 발생
        long completedCares = careRequestRepository.countByCompletedAtBetween(startOfDay, endOfDay);
        long activeUsers = usersRepository.countByLastLoginAtBetween(startOfDay, endOfDay);
        long newMeetups = meetupRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long meetupParticipants = meetupParticipantsRepository.countByJoinedAtBetween(startOfDay, endOfDay);
        long newReports = reportRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        return DailyStatistics.builder()
                .statDate(today)
                .newUsers((int) newUsers)
                .newPosts((int) newPosts)
                .newCareRequests((int) newCareRequests)
                .completedCares((int) completedCares)
                .totalRevenue(BigDecimal.ZERO)
                .activeUsers((int) activeUsers)
                .newMeetups((int) newMeetups)
                .meetupParticipants((int) meetupParticipants)
                .newReports((int) newReports)
                .build();
    }
}
