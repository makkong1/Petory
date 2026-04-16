package com.linkup.Petory.domain.meetup.repository;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * SpringDataJpaMeetupRepository.findAvailableMeetups() 성능·동치 측정 테스트
 * ====================================================================================
 * 
 * 📌 목적: 서브쿼리 성능 문제 측정 및 리팩토링 전/후 성능 비교
 * 
 * 📊 측정 항목:
 * - 쿼리 수 (Hibernate Statistics 사용) - 서브쿼리 실행 횟수 확인
 * - 실행 시간 (밀리초)
 * - 메모리 사용량 (MB)
 * - 조회된 참여 가능한 모임 수
 * 
 * 📝 실행 방법:
 * 1. IDE에서 테스트 메서드 우클릭 → Run
 * 2. 또는: ./gradlew test --tests MeetupRepositorySubqueryPerformanceTest
 * 
 * ====================================================================================
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeetupRepositorySubqueryPerformanceTest {

    @Autowired
    private SpringDataJpaMeetupRepository meetupRepository;

    @Autowired
    private MeetupParticipantsRepository meetupParticipantsRepository;

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Users testOrganizer;
    private List<Meetup> testMeetups;
    private List<Users> testUsers;
    private LocalDateTime currentDate;
    
    // 테스트 파라미터
    private static final int TOTAL_MEETUP_COUNT = 100; // 전체 모임 수
    private static final int PARTICIPANTS_PER_MEETUP_MIN = 0; // 모임당 최소 참여자 수
    private static final int PARTICIPANTS_PER_MEETUP_MAX = 8; // 모임당 최대 참여자 수 (maxParticipants는 10)

    @BeforeEach
    void setUp() {
        // 현재 시간 기준 설정
        currentDate = LocalDateTime.now();
        
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

        // 테스트 사용자들 생성 (참여자용)
        testUsers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Users user = Users.builder()
                    .id("user_" + timestamp + "_" + i)
                    .username("user_" + timestamp + "_" + i)
                    .email("user_" + timestamp + "_" + i + "@example.com")
                    .password("password")
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build();
            testUsers.add(usersRepository.save(user));
        }

        // 다양한 날짜와 참여자 수를 가진 meetup 생성
        testMeetups = new ArrayList<>();
        for (int i = 0; i < TOTAL_MEETUP_COUNT; i++) {
            // 일부는 과거 날짜 (필터링되어야 함)
            // 일부는 미래 날짜 (조회 대상)
            LocalDateTime meetupDate = i < TOTAL_MEETUP_COUNT / 2 
                    ? currentDate.plusDays(i % 30) // 미래 날짜
                    : currentDate.minusDays(i % 30); // 과거 날짜
            
            // 일부는 삭제된 모임
            boolean isDeleted = i % 10 == 0;
            
            Meetup meetup = Meetup.builder()
                    .title("테스트 모임 " + i)
                    .description("테스트 설명 " + i)
                    .date(meetupDate)
                    .latitude(37.5665 + (Math.random() - 0.5) * 0.1)
                    .longitude(126.9780 + (Math.random() - 0.5) * 0.1)
                    .maxParticipants(10)
                    .currentParticipants(0)
                    .organizer(testOrganizer)
                    .status(i % 5 == 0 ? MeetupStatus.COMPLETED : MeetupStatus.RECRUITING)
                    .isDeleted(isDeleted)
                    .build();
            testMeetups.add(meetupRepository.save(meetup));
        }

        // 각 meetup에 랜덤한 수의 참여자 추가
        for (int i = 0; i < TOTAL_MEETUP_COUNT; i++) {
            Meetup meetup = testMeetups.get(i);
            int participantCount = PARTICIPANTS_PER_MEETUP_MIN + 
                    (int)(Math.random() * (PARTICIPANTS_PER_MEETUP_MAX - PARTICIPANTS_PER_MEETUP_MIN + 1));
            
            for (int j = 0; j < participantCount; j++) {
                Users participant = testUsers.get(j % testUsers.size());
                MeetupParticipants mp = MeetupParticipants.builder()
                        .meetup(meetup)
                        .user(participant)
                        .joinedAt(currentDate.minusDays(j))
                        .build();
                meetupParticipantsRepository.save(mp);
            }

            // 저장된 참가 행 수와 동기화 (동일 user 중복 등으로 루프 횟수 ≠ 행 수일 수 있음)
            entityManager.flush();
            int actualCount = meetupParticipantsRepository.countByMeetupIdx(meetup.getIdx()).intValue();
            meetup.setCurrentParticipants(actualCount);
            meetupRepository.save(meetup);
        }

        // 영속성 컨텍스트 초기화 (실제 DB 쿼리 발생하도록)
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("리팩토링 전/후 findAvailableMeetups() 성능 비교")
    void testFindAvailableMeetups_Comparison() {
        // ========== [1단계] 베이스라인: 리포지토리와 동일 조건(JPQL, organizer 미페치) ==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📌 [1단계] 베이스라인: currentParticipants·RECRUITING 조건 (JOIN FETCH 없음)");
        System.out.println("=".repeat(80));
        
        // Hibernate Statistics 초기화
        Session session = entityManager.unwrap(Session.class);
        Statistics statistics = session.getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // 메모리 측정 시작
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 성능 측정 시작
        long startTime = System.currentTimeMillis();

        // DB 쿼리 실행 — findAvailableMeetups와 동일 도메인 조건(참여 가능 정의 일치)
        long dbStartTime = System.currentTimeMillis();
        List<Meetup> meetupsBefore = entityManager.createQuery(
                "SELECT m FROM Meetup m WHERE m.date > :currentDate "
                        + "AND m.currentParticipants < m.maxParticipants "
                        + "AND m.status = :recruiting "
                        + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
                        + "ORDER BY m.date ASC",
                Meetup.class)
                .setParameter("currentDate", currentDate)
                .setParameter("recruiting", MeetupStatus.RECRUITING)
                .getResultList();
        long dbTimeBefore = System.currentTimeMillis() - dbStartTime;

        long totalTimeBefore = System.currentTimeMillis() - startTime;

        // 메모리 측정 종료
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsedBefore = memoryAfter - memoryBefore;

        // Hibernate Statistics 수집
        long queryCountBefore = statistics.getQueryExecutionCount();
        long prepareStatementCountBefore = statistics.getPrepareStatementCount();
        long entityLoadCountBefore = statistics.getEntityLoadCount();

        // 결과 출력
        System.out.println("테스트 환경:");
        System.out.println("- 전체 모임 수: " + TOTAL_MEETUP_COUNT + " 개");
        System.out.println("- 현재 날짜: " + currentDate);
        System.out.println("- 모임당 참여자 수 범위: " + PARTICIPANTS_PER_MEETUP_MIN + " ~ " + PARTICIPANTS_PER_MEETUP_MAX);
        System.out.println();
        System.out.println("📊 성능 측정 결과 (1단계 - 베이스라인 JPQL)");
        System.out.println("⏱️  실행 시간: " + totalTimeBefore + " ms");
        System.out.println("   └─ DB 쿼리: " + dbTimeBefore + " ms");
        System.out.println("🔢 쿼리 수: " + queryCountBefore + " 개");
        System.out.println("   └─ PrepareStatement 횟수: " + prepareStatementCountBefore);
        System.out.println("💾 메모리 사용량: " + memoryUsedBefore + " bytes (" + (memoryUsedBefore / (1024.0 * 1024.0)) + " MB)");
        System.out.println("📋 조회된 참여 가능한 모임 수: " + meetupsBefore.size() + " 개");

        // 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // ========== [2단계] Spring Data — JOIN FETCH organizer + 동일 WHERE ==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📌 [2단계] findAvailableMeetups (JOIN FETCH organizer)");
        System.out.println("=".repeat(80));

        // 메모리 측정 시작
        System.gc();
        memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 성능 측정 시작
        startTime = System.currentTimeMillis();

        // DB 쿼리 실행 (리팩토링 후 - Repository 메서드 사용)
        dbStartTime = System.currentTimeMillis();
        List<Meetup> meetupsAfter = meetupRepository.findAvailableMeetups(
                currentDate, MeetupStatus.RECRUITING, Pageable.unpaged());
        long dbTimeAfter = System.currentTimeMillis() - dbStartTime;

        long totalTimeAfter = System.currentTimeMillis() - startTime;

        // 메모리 측정 종료
        memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsedAfter = memoryAfter - memoryBefore;

        // Hibernate Statistics 수집
        long queryCountAfter = statistics.getQueryExecutionCount();
        long prepareStatementCountAfter = statistics.getPrepareStatementCount();
        long entityLoadCountAfter = statistics.getEntityLoadCount();

        // 결과 출력
        System.out.println("📊 성능 측정 결과 (2단계 - Repository)");
        System.out.println("⏱️  실행 시간: " + totalTimeAfter + " ms");
        System.out.println("   └─ DB 쿼리: " + dbTimeAfter + " ms");
        System.out.println("🔢 쿼리 수: " + queryCountAfter + " 개");
        System.out.println("   └─ PrepareStatement 횟수: " + prepareStatementCountAfter);
        System.out.println("💾 메모리 사용량: " + memoryUsedAfter + " bytes (" + (memoryUsedAfter / (1024.0 * 1024.0)) + " MB)");
        System.out.println("📋 조회된 참여 가능한 모임 수: " + meetupsAfter.size() + " 개");

        // ========== [3단계] 결과 비교 ==========
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🎯 성능 개선 결과");
        System.out.println("=".repeat(80));
        
        long prepareStatementImprovement = prepareStatementCountBefore - prepareStatementCountAfter;
        double prepareStatementImprovementRate = prepareStatementCountBefore > 0 
                ? (double) prepareStatementImprovement / prepareStatementCountBefore * 100 
                : 0;
        
        long timeImprovement = totalTimeBefore - totalTimeAfter;
        double timeImprovementRate = totalTimeBefore > 0 
                ? (double) timeImprovement / totalTimeBefore * 100 
                : 0;
        
        long memoryImprovement = memoryUsedBefore - memoryUsedAfter;
        double memoryImprovementRate = memoryUsedBefore > 0 
                ? (double) memoryImprovement / memoryUsedBefore * 100 
                : 0;

        System.out.println("| 항목 | Before | After | 개선 | 개선율 |");
        System.out.println("|------|--------|-------|------|--------|");
        System.out.println("| **실행 시간** | " + totalTimeBefore + " ms | " + totalTimeAfter + " ms | " + 
                (timeImprovement >= 0 ? "-" : "+") + Math.abs(timeImprovement) + " ms | " + 
                String.format("%.1f", Math.abs(timeImprovementRate)) + "% " + (timeImprovement >= 0 ? "⬇️" : "⬆️") + " |");
        System.out.println("| **DB 쿼리 시간** | " + dbTimeBefore + " ms | " + dbTimeAfter + " ms | " + 
                ((dbTimeBefore - dbTimeAfter) >= 0 ? "-" : "+") + Math.abs(dbTimeBefore - dbTimeAfter) + " ms | |");
        System.out.println("| **PrepareStatement 수** | " + prepareStatementCountBefore + " 개 | " + prepareStatementCountAfter + " 개 | " + 
                (prepareStatementImprovement >= 0 ? "-" : "+") + Math.abs(prepareStatementImprovement) + " 개 | " + 
                String.format("%.1f", Math.abs(prepareStatementImprovementRate)) + "% " + (prepareStatementImprovement >= 0 ? "⬇️" : "⬆️") + " |");
        System.out.println("| **메모리 사용량** | " + String.format("%.2f", memoryUsedBefore / (1024.0 * 1024.0)) + " MB | " + 
                String.format("%.2f", memoryUsedAfter / (1024.0 * 1024.0)) + " MB | " + 
                (memoryImprovement >= 0 ? "-" : "+") + String.format("%.2f", Math.abs(memoryImprovement) / (1024.0 * 1024.0)) + " MB | " + 
                String.format("%.1f", Math.abs(memoryImprovementRate)) + "% " + (memoryImprovement >= 0 ? "⬇️" : "⬆️") + " |");
        System.out.println("| **조회된 모임 수** | " + meetupsBefore.size() + " 개 | " + meetupsAfter.size() + " 개 | " + 
                (meetupsBefore.size() == meetupsAfter.size() ? "동일 ✅" : "차이 있음 ⚠️") + " | |");
        System.out.println("=".repeat(80));

        // 검증
        assertThat(meetupsBefore).isNotNull();
        assertThat(meetupsAfter).isNotNull();
        assertThat(meetupsBefore.size()).isGreaterThan(0);
        assertThat(meetupsAfter.size()).isGreaterThan(0);
        
        // 동일 도메인 조건이면 idx 집합이 같아야 함(JOIN FETCH로 행 중복이 있어도 idx 기준 비교)
        List<Long> beforeIdxes = meetupsBefore.stream().map(Meetup::getIdx).distinct().sorted().toList();
        List<Long> afterIdxes = meetupsAfter.stream().map(Meetup::getIdx).distinct().sorted().toList();
        assertThat(meetupsAfter.size()).isGreaterThanOrEqualTo(beforeIdxes.size());
        assertThat(afterIdxes).containsExactlyElementsOf(beforeIdxes);
        
        System.out.println("✅ 검증 완료: 리팩토링 전후 결과가 동일합니다.");
    }
}
