package com.linkup.Petory.domain.statistics.service;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsScheduler {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final ReportRepository reportRepository;

    // application.properties에서 스케줄러 시간 읽기 (기본값: 18:30)
    @Value("${statistics.scheduler.hour:18}")
    private int schedulerHour;

    @Value("${statistics.scheduler.minute:30}")
    private int schedulerMinute;

    /**
     * 매일 지정된 시간에 실행되어 '어제'의 통계를 집계
     * 
     * 기본값: 매일 오후 6시 30분(18:30:00)
     * 설정 변경: application.properties에 다음 추가
     *   statistics.scheduler.hour=18
     *   statistics.scheduler.minute=30
     * 
     * 주의: @Scheduled는 컴파일 타임에 결정되므로, 동적 변경을 위해서는
     *       TaskScheduler를 사용한 동적 스케줄러 구현이 필요합니다.
     *       현재는 application.properties 수정 후 서버 재시작이 필요합니다.
     */
    @Scheduled(cron = "${statistics.scheduler.cron:0 30 18 * * ?}")
    @Transactional
    public void aggregateDailyStatistics() {
        log.info("통계 집계 스케줄러 실행 (설정된 시간: {}:{}), 어제 통계 집계 시작", schedulerHour, schedulerMinute);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // [FIX] 스케줄러 실패 시 예외 전파 차단 — 집계 실패가 서비스 전체에 영향을 주지 않도록 처리
        try {
            aggregateStatisticsForDate(yesterday);
        } catch (Exception e) {
            log.error("통계 집계 스케줄러 실패 — 날짜: {}, 원인: {}. 수동 복구: POST /api/admin/statistics/init?days=1",
                    yesterday, e.getMessage(), e);
        }
    }

    /**
     * 특정 날짜의 통계 집계 (재사용 가능)
     */
    @Transactional
    public void aggregateStatisticsForDate(LocalDate date) {
        log.info("일일 통계 집계 시작: {}", date);

        // 이미 집계된 데이터가 있는지 확인 (중복 방지)
        if (dailyStatisticsRepository.findByStatDate(date).isPresent()) {
            log.warn("이미 {}의 통계가 존재합니다. 집계를 건너뜁니다.", date);
            return;
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 1. 신규 가입자
        long newUsers = usersRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 2. 새 게시글
        long newPosts = boardRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 3. 케어 요청
        long newCareRequests = careRequestRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 4. 완료된 케어
        // [FIX] date(케어 예정일) → completedAt(실제 완료 시각)으로 교체 — 기존은 완료 날짜가 아닌 예정일 기준으로 집계
        long completedCares = careRequestRepository.countByCompletedAtBetween(startOfDay, endOfDay);

        // 5. 매출 (현재 0으로 고정)
        BigDecimal totalRevenue = BigDecimal.ZERO;

        // 6. DAU
        long activeUsers = usersRepository.countByLastLoginAtBetween(startOfDay, endOfDay);

        // 7. 새 모임
        long newMeetups = meetupRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 8. 모임 참여
        long meetupParticipants = meetupParticipantsRepository.countByJoinedAtBetween(startOfDay, endOfDay);

        // 9. 신고 접수
        long newReports = reportRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        DailyStatistics stats = DailyStatistics.builder()
                .statDate(date)
                .newUsers((int) newUsers)
                .newPosts((int) newPosts)
                .newCareRequests((int) newCareRequests)
                .completedCares((int) completedCares)
                .totalRevenue(totalRevenue)
                .activeUsers((int) activeUsers)
                .newMeetups((int) newMeetups)
                .meetupParticipants((int) meetupParticipants)
                .newReports((int) newReports)
                .build();

        dailyStatisticsRepository.save(stats);

        log.info("일일 통계 집계 완료: {}", stats);
    }

    /**
     * 과거 데이터 초기화 (Backfill)
     * 오늘 기준 days일 전부터 어제까지 집계
     */
    @Transactional
    public void backfillStatistics(int days) {
        LocalDate today = LocalDate.now();
        for (int i = days; i > 0; i--) {
            LocalDate targetDate = today.minusDays(i);
            aggregateStatisticsForDate(targetDate);
        }
    }
}
