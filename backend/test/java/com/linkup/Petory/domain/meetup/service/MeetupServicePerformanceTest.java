package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * MeetupService.getNearbyMeetups() 성능 측정 테스트
 * ====================================================================================
 * 
 * 📌 목적: 리팩토링 전 현재 성능 상태를 측정하여 베이스라인 확보
 * 
 * 📊 측정 항목:
 * - 쿼리 수 (Hibernate Statistics 사용)
 * - 실행 시간 (밀리초)
 * - 메모리 사용량 (MB)
 * - 전체 meetup 수 vs 결과 meetup 수
 * 
 * 📝 실행 방법:
 * 1. IDE에서 테스트 메서드 우클릭 → Run
 * 2. 또는: ./gradlew test --tests MeetupServicePerformanceTest
 * 
 * ⚠️ 주의: 이 테스트는 리팩토링 전 현재 상태를 측정합니다.
 * 리팩토링 후에는 이 테스트를 수정하여 Before/After 비교를 수행합니다.
 * 
 * ====================================================================================
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeetupServicePerformanceTest {

    @Autowired
    private MeetupService meetupService;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Users testOrganizer;
    private List<Meetup> testMeetups;
    
    // 테스트 파라미터
    private static final int TOTAL_MEETUP_COUNT = 1000; // 전체 meetup 수
    private static final double TEST_LAT = 37.5665; // 서울시청 위도
    private static final double TEST_LNG = 126.9780; // 서울시청 경도
    private static final double TEST_RADIUS = 5.0; // 반경 5km

    @BeforeEach
    void setUp() {
        // 테스트 주최자 생성
        long timestamp = System.currentTimeMillis();
        testOrganizer = Users.builder()
                .id("organizer_" + timestamp)
                .username("organizer_" + timestamp)
                .email("organizer_" + timestamp + "@example.com")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        testOrganizer = usersRepository.save(testOrganizer);

        // 다양한 위치와 날짜의 meetup 생성
        testMeetups = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < TOTAL_MEETUP_COUNT; i++) {
            // 위치: 서울 중심에서 랜덤하게 분산 (반경 0~20km)
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * 20.0; // 0~20km
            double latOffset = distance * Math.cos(angle) / 111.0; // 대략적인 위도 차이
            double lngOffset = distance * Math.sin(angle) / (111.0 * Math.cos(Math.toRadians(TEST_LAT)));
            
            double lat = TEST_LAT + latOffset;
            double lng = TEST_LNG + lngOffset;
            
            // 날짜: 과거, 현재, 미래 랜덤하게
            LocalDateTime date;
            int dateType = i % 3;
            if (dateType == 0) {
                date = now.minusDays((int)(Math.random() * 30)); // 과거
            } else if (dateType == 1) {
                date = now.plusDays((int)(Math.random() * 30)); // 미래
            } else {
                date = now.plusDays((int)(Math.random() * 60)); // 더 먼 미래
            }
            
            // 상태: RECRUITING, CLOSED, COMPLETED 랜덤하게
            MeetupStatus status;
            int statusType = i % 3;
            if (statusType == 0) {
                status = MeetupStatus.RECRUITING;
            } else if (statusType == 1) {
                status = MeetupStatus.CLOSED;
            } else {
                status = MeetupStatus.COMPLETED;
            }
            
            Meetup meetup = Meetup.builder()
                    .title("테스트 모임 " + i)
                    .description("성능 테스트용 모임 " + i)
                    .location("서울시")
                    .latitude(lat)
                    .longitude(lng)
                    .date(date)
                    .organizer(testOrganizer)
                    .maxParticipants(10)
                    .currentParticipants(1)
                    .status(status)
                    .isDeleted(false)
                    .build();
            
            testMeetups.add(meetupRepository.save(meetup));
        }

        // 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * ====================================================================================
     * 리팩토링 전 성능 측정 (현재 상태)
     * ====================================================================================
     * 
     * 현재 구현:
     * - findAllNotDeleted()로 전체 meetup 로드
     * - Java에서 거리 계산 및 필터링
     * - 여러 번의 Stream 연산
     * 
     * 예상 문제점:
     * - O(n) 메모리 사용 (전체 meetup 로드)
     * - Java에서 거리 계산 (n번 수행)
     * - 여러 번의 Stream pass
     * 
     * ====================================================================================
     */
    @Test
    @DisplayName("리팩토링 전 getNearbyMeetups() 성능 측정")
    void measurePerformanceBeforeRefactoring() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📌 리팩토링 전 성능 측정: getNearbyMeetups()");
        System.out.println("=".repeat(80));
        System.out.println("테스트 환경:");
        System.out.println("  - 전체 meetup 수: " + TOTAL_MEETUP_COUNT + " 개");
        System.out.println("  - 조회 위치: lat=" + TEST_LAT + ", lng=" + TEST_LNG);
        System.out.println("  - 반경: " + TEST_RADIUS + " km");
        System.out.println();

        // Hibernate Statistics 활성화
        Session session = entityManager.unwrap(Session.class);
        Statistics statistics = session.getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // 메모리 측정 시작
        Runtime runtime = Runtime.getRuntime();
        System.gc(); // GC 실행하여 정확한 측정
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 시간 측정 시작
        long startTime = System.currentTimeMillis();

        // 쿼리 수 측정 시작
        long queryCountBefore = statistics.getQueryExecutionCount();

        // 실제 메서드 호출
        List<MeetupDTO> result = meetupService.getNearbyMeetups(TEST_LAT, TEST_LNG, TEST_RADIUS);

        // 측정 종료
        long endTime = System.currentTimeMillis();
        long queryCountAfter = statistics.getQueryExecutionCount();
        
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long executionTime = endTime - startTime;
        long queryCount = queryCountAfter - queryCountBefore;
        long memoryUsed = memoryAfter - memoryBefore;

        // 결과 출력
        System.out.println("=".repeat(80));
        System.out.println("📊 성능 측정 결과 (리팩토링 전)");
        System.out.println("=".repeat(80));
        System.out.println(String.format("⏱️  실행 시간: %,d ms", executionTime));
        System.out.println(String.format("📊 쿼리 수: %,d 개", queryCount));
        System.out.println(String.format("💾 메모리 사용량: %,d bytes (%.2f MB)",
                memoryUsed, memoryUsed / (1024.0 * 1024.0)));
        System.out.println(String.format("📈 전체 meetup 수: %,d 개", TOTAL_MEETUP_COUNT));
        System.out.println(String.format("✅ 결과 meetup 수: %,d 개", result.size()));
        System.out.println(String.format("📉 필터링율: %.2f%%", 
                (1.0 - (double)result.size() / TOTAL_MEETUP_COUNT) * 100));
        System.out.println("=".repeat(80));
        System.out.println();

        // 상세 분석
        System.out.println("📋 상세 분석:");
        System.out.println("  - 현재 구현: findAllNotDeleted()로 전체 로드 후 Java에서 필터링");
        System.out.println("  - 메모리 복잡도: O(n) - 전체 meetup 로드");
        System.out.println("  - 시간 복잡도: O(n log n) - 거리 계산 + 정렬");
        System.out.println("  - 예상 개선 포인트:");
        System.out.println("    * DB 쿼리로 필터링 이동 → 메모리 사용량 감소");
        System.out.println("    * DB에서 거리 계산 → Java 거리 계산 제거");
        System.out.println("    * Stream 연산 최소화 → 코드 간소화");
        System.out.println();

        // 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.size()).isLessThanOrEqualTo(TOTAL_MEETUP_COUNT);
        
        // 통계 비활성화
        statistics.setStatisticsEnabled(false);

        // 결과를 파일로 저장할 수도 있음 (선택사항)
        // saveResultsToFile(executionTime, queryCount, memoryUsed, result.size());
    }

    /**
     * 결과를 파일로 저장 (선택사항)
     */
    private void saveResultsToFile(long executionTime, long queryCount, long memoryUsed, int resultCount) {
        // 필요시 구현
    }
}
