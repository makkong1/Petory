package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MeetupService Race Condition 테스트
 * 
 * 문제 시나리오:
 * - 모임 최대 인원: 3명
 * - 모임장 1명 (이미 참가) → currentParticipants = 1
 * - 남은 자리: 2명
 * - 동시에 3명이 참가 버튼 클릭
 * - 3명 모두 currentParticipants (1) < maxParticipants (3) 체크 통과
 * - 3명 모두 참가 처리
 * - 결과: currentParticipants = 1 + 3 = 4명 → 최대 인원 초과!
 */
@SpringBootTest
@ActiveProfiles("test")
class MeetupServiceRaceConditionTest {

    @Autowired
    private MeetupService meetupService;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private MeetupParticipantsRepository meetupParticipantsRepository;

    @Autowired
    private UsersRepository usersRepository;

    private Meetup testMeetup;
    private Users organizer;
    private List<Users> participants;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 데이터 사용
        long timestamp = System.currentTimeMillis();

        // 모임장 생성
        organizer = Users.builder()
                .id("organizer_" + timestamp)
                .username("organizer_" + timestamp)
                .email("organizer_" + timestamp + "@example.com")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        organizer = usersRepository.save(organizer);

        // 모임 생성 (최대 인원 3명)
        testMeetup = Meetup.builder()
                .title("테스트 모임 " + timestamp)
                .description("Race Condition 테스트용 모임")
                .location("서울시 강남구")
                .latitude(37.5665)
                .longitude(126.9780)
                .date(LocalDateTime.now().plusDays(1))
                .organizer(organizer)
                .maxParticipants(3)
                .currentParticipants(1) // 모임장 포함
                .status(MeetupStatus.RECRUITING)
                .build();
        testMeetup = meetupRepository.save(testMeetup);

        // 모임장을 참가자로 추가
        com.linkup.Petory.domain.meetup.entity.MeetupParticipants organizerParticipant = com.linkup.Petory.domain.meetup.entity.MeetupParticipants
                .builder()
                .meetup(testMeetup)
                .user(organizer)
                .joinedAt(LocalDateTime.now())
                .build();
        meetupParticipantsRepository.save(organizerParticipant);

        // 테스트용 참가자 3명 생성
        participants = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Users participant = Users.builder()
                    .id("participant_" + timestamp + "_" + i)
                    .username("participant_" + timestamp + "_" + i)
                    .email("participant_" + timestamp + "_" + i + "@example.com")
                    .password("password")
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build();
            participant = usersRepository.save(participant);
            participants.add(participant);
        }

        System.out.println("=== 테스트 설정 완료 ===");
        System.out.println("모임 ID: " + testMeetup.getIdx());
        System.out.println("최대 인원: " + testMeetup.getMaxParticipants());
        System.out.println("현재 인원: " + testMeetup.getCurrentParticipants());
        System.out.println("남은 자리: " + (testMeetup.getMaxParticipants() - testMeetup.getCurrentParticipants()));
        System.out.println("동시 참가 시도: 3명");
        System.out.println("======================");
    }

    @Test
    @DisplayName("Race Condition 재현 - READ COMMITTED 격리 수준으로 실제 Race Condition 발생 확인")
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    void testRaceConditionWithReadCommitted() throws InterruptedException {
        Long meetupIdx = testMeetup.getIdx();
        int attemptCount = 3; // 동시 참가 시도 인원

        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch startLatch = new CountDownLatch(attemptCount);
        CountDownLatch readyLatch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<String> joinLogs = Collections.synchronizedList(new ArrayList<>());

        System.out.println("\n=== Race Condition 테스트 (READ COMMITTED) ===");
        System.out.println("격리 수준: READ COMMITTED (Lost Update 발생 가능)");
        System.out.println("동시 참가 시도: " + attemptCount + "명");
        System.out.println("예상 결과: Race Condition 발생하여 인원 초과 가능");
        System.out.println("=============================================\n");

        // 동시에 3명이 참가 시도
        for (int i = 0; i < attemptCount; i++) {
            final int userIndex = i;
            final Users user = participants.get(i);
            final String userId = user.getId();

            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    readyLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    long startTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    System.out.println("[시작] " + threadName + " - 사용자 " + userIndex + " 참가 시도 시작");
                    joinLogs.add(String.format("[%s] 사용자%d 참가 시도 시작",
                            LocalDateTime.now().toString(), userIndex));

                    // 참가 시도 (READ COMMITTED 격리 수준)
                    meetupService.joinMeetup(meetupIdx, userId);

                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    // 참가 후 상태 확인
                    Meetup afterMeetup = meetupRepository.findById(meetupIdx).orElse(null);
                    if (afterMeetup != null) {
                        System.out.println(String.format("[성공] %s - 사용자 %d 참가 성공 (소요시간: %dms) - 현재 인원: %d/%d",
                                threadName, userIndex, duration,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                        joinLogs.add(String.format("[%s] 사용자%d 참가 성공 - 현재 인원: %d/%d",
                                LocalDateTime.now().toString(), userIndex,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                    }

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    long endTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    String errorMsg = e.getMessage();
                    System.out.println(String.format("[실패] %s - 사용자 %d 참가 실패: %s",
                            threadName, userIndex, errorMsg));
                    joinLogs.add(String.format("[%s] 사용자%d 참가 실패: %s",
                            LocalDateTime.now().toString(), userIndex, errorMsg));

                    exceptions.add(e);
                    failureCount.incrementAndGet();
                }
            });

            readyLatch.countDown();
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!terminated) {
            System.out.println("경고: 일부 스레드가 10초 내에 완료되지 않았습니다.");
            executor.shutdownNow();
        }

        // 최종 상태 확인
        Meetup finalMeetup = meetupRepository.findById(meetupIdx).orElse(null);
        assertNotNull(finalMeetup, "모임이 존재해야 함");

        long actualParticipantCount = meetupParticipantsRepository.countByMeetupIdx(meetupIdx);

        System.out.println("\n=== 테스트 결과 ===");
        System.out.println("성공한 참가: " + successCount.get() + "명");
        System.out.println("실패한 참가: " + failureCount.get() + "명");
        System.out.println("최종 currentParticipants: " + finalMeetup.getCurrentParticipants());
        System.out.println("실제 참가자 수 (DB): " + actualParticipantCount);
        System.out.println("최대 인원: " + finalMeetup.getMaxParticipants());
        System.out.println("==================\n");

        // 상세 로그 출력
        System.out.println("=== 상세 로그 ===");
        joinLogs.forEach(System.out::println);
        System.out.println("================\n");

        // 검증: Race Condition 발생 여부 확인
        // 1. Lost Update 감지: 실제 참가자 수와 currentParticipants가 일치하지 않는 경우
        boolean lostUpdateDetected = (int) actualParticipantCount != finalMeetup.getCurrentParticipants();

        // 2. 인원 초과 감지: 실제 참가자 수가 최대 인원을 초과하는 경우
        boolean overCapacityDetected = actualParticipantCount > finalMeetup.getMaxParticipants();

        // 3. 저장된 값이 최대 인원을 초과하는 경우
        boolean storedValueOverCapacity = finalMeetup.getCurrentParticipants() > finalMeetup.getMaxParticipants();

        // Race Condition 발생 여부 (위 세 가지 중 하나라도 발생하면)
        boolean raceConditionDetected = lostUpdateDetected || overCapacityDetected || storedValueOverCapacity;

        System.out.println("\n=== Race Condition 분석 ===");
        System.out.println("Lost Update 발생: " + (lostUpdateDetected ? "✅ YES" : "❌ NO"));
        if (lostUpdateDetected) {
            System.out.println("   → 실제 참가자 수(" + actualParticipantCount +
                    ")와 저장된 currentParticipants(" + finalMeetup.getCurrentParticipants() + ") 불일치");
            System.out.println("   → 여러 트랜잭션이 동시에 읽고 업데이트하여 마지막 값만 저장됨");
        }

        System.out.println("인원 초과 발생: " + (overCapacityDetected ? "✅ YES" : "❌ NO"));
        if (overCapacityDetected) {
            System.out.println("   → 실제 참가자 수(" + actualParticipantCount +
                    ")가 최대 인원(" + finalMeetup.getMaxParticipants() + ")을 초과");
        }

        System.out.println("저장값 초과: " + (storedValueOverCapacity ? "✅ YES" : "❌ NO"));
        if (storedValueOverCapacity) {
            System.out.println("   → 저장된 currentParticipants(" + finalMeetup.getCurrentParticipants() +
                    ")가 최대 인원(" + finalMeetup.getMaxParticipants() + ")을 초과");
        }
        System.out.println("========================\n");

        if (raceConditionDetected) {
            System.out.println("⚠️ Race Condition 발생 확인!");
            System.out.println("   ✅ Race Condition이 실제로 발생했습니다!");
            System.out.println("   → 이제 해결 방법을 적용해야 합니다.");
        } else {
            System.out.println("✅ Race Condition 미발생");
        }

        // 예외 로그 출력
        if (!exceptions.isEmpty()) {
            System.out.println("\n=== 발생한 예외들 ===");
            exceptions.forEach(e -> System.out.println("  - " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            System.out.println("==================\n");
        }
    }

    @Test
    @DisplayName("Race Condition 재현 - 동시에 3명이 참가 시도하여 인원 초과 발생")
    void testRaceConditionConcurrentJoin() throws InterruptedException {
        Long meetupIdx = testMeetup.getIdx();
        int attemptCount = 3; // 동시 참가 시도 인원

        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch startLatch = new CountDownLatch(attemptCount);
        CountDownLatch readyLatch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<String> joinLogs = Collections.synchronizedList(new ArrayList<>());

        System.out.println("\n=== Race Condition 테스트 시작 ===");
        System.out.println("동시 참가 시도: " + attemptCount + "명");
        System.out.println("예상 결과: 2명 성공, 1명 실패 (최대 인원 3명)");
        System.out.println("⚠️ 주의: MySQL의 REPEATABLE READ 격리 수준으로 인해 Deadlock이 발생할 수 있음");
        System.out.println("===============================\n");

        // 동시에 3명이 참가 시도
        for (int i = 0; i < attemptCount; i++) {
            final int userIndex = i;
            final Users user = participants.get(i);
            final String userId = user.getId();

            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    readyLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    long startTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    System.out.println("[시작] " + threadName + " - 사용자 " + userIndex + " 참가 시도 시작");
                    joinLogs.add(String.format("[%s] 사용자%d 참가 시도 시작",
                            LocalDateTime.now().toString(), userIndex));

                    // 참가 전 상태 확인 (트랜잭션 외부에서 조회하여 실제 DB 상태 확인)
                    // ⚠️ 트랜잭션 격리 수준 때문에 각 트랜잭션이 다른 값을 볼 수 있음
                    Meetup beforeMeetup = meetupRepository.findById(meetupIdx).orElse(null);
                    if (beforeMeetup != null) {
                        System.out.println(String.format("[체크 전] %s - 모임 현재 인원: %d/%d",
                                threadName, beforeMeetup.getCurrentParticipants(), beforeMeetup.getMaxParticipants()));
                        joinLogs.add(String.format("[%s] 사용자%d 체크 전 - 현재 인원: %d/%d",
                                LocalDateTime.now().toString(), userIndex,
                                beforeMeetup.getCurrentParticipants(), beforeMeetup.getMaxParticipants()));
                    }

                    // ⚠️ Race Condition 재현을 위한 의도적 딜레이
                    // 체크와 업데이트 사이에 다른 트랜잭션이 끼어들 수 있도록 함
                    // 하지만 MySQL의 REPEATABLE READ로 인해 실제로는 Deadlock 발생 가능
                    Thread.sleep(10); // 10ms 딜레이

                    // 참가 시도
                    meetupService.joinMeetup(meetupIdx, userId);

                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    // 참가 후 상태 확인
                    Meetup afterMeetup = meetupRepository.findById(meetupIdx).orElse(null);
                    if (afterMeetup != null) {
                        System.out.println(String.format("[성공] %s - 사용자 %d 참가 성공 (소요시간: %dms) - 현재 인원: %d/%d",
                                threadName, userIndex, duration,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                        joinLogs.add(String.format("[%s] 사용자%d 참가 성공 - 현재 인원: %d/%d",
                                LocalDateTime.now().toString(), userIndex,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                    }

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    long endTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("Deadlock")) {
                        System.out.println(String.format("[Deadlock] %s - 사용자 %d Deadlock 발생: %s",
                                threadName, userIndex, e.getClass().getSimpleName()));
                        joinLogs.add(String.format("[%s] 사용자%d Deadlock 발생: %s",
                                LocalDateTime.now().toString(), userIndex, e.getClass().getSimpleName()));
                    } else {
                        System.out.println(String.format("[실패] %s - 사용자 %d 참가 실패: %s",
                                threadName, userIndex, errorMsg));
                        joinLogs.add(String.format("[%s] 사용자%d 참가 실패: %s",
                                LocalDateTime.now().toString(), userIndex, errorMsg));
                    }

                    exceptions.add(e);
                    failureCount.incrementAndGet();
                }
            });

            readyLatch.countDown();
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!terminated) {
            System.out.println("경고: 일부 스레드가 10초 내에 완료되지 않았습니다.");
            executor.shutdownNow();
        }

        // 최종 상태 확인
        Meetup finalMeetup = meetupRepository.findById(meetupIdx).orElse(null);
        assertNotNull(finalMeetup, "모임이 존재해야 함");

        long actualParticipantCount = meetupParticipantsRepository.countByMeetupIdx(meetupIdx);

        System.out.println("\n=== 테스트 결과 ===");
        System.out.println("성공한 참가: " + successCount.get() + "명");
        System.out.println("실패한 참가: " + failureCount.get() + "명");
        System.out.println("최종 currentParticipants: " + finalMeetup.getCurrentParticipants());
        System.out.println("실제 참가자 수 (DB): " + actualParticipantCount);
        System.out.println("최대 인원: " + finalMeetup.getMaxParticipants());
        System.out.println("==================\n");

        // 상세 로그 출력
        System.out.println("=== 상세 로그 ===");
        joinLogs.forEach(System.out::println);
        System.out.println("================\n");

        // 검증: Race Condition 발생 여부 확인
        boolean raceConditionDetected = finalMeetup.getCurrentParticipants() > finalMeetup.getMaxParticipants();
        boolean deadlockOccurred = exceptions.stream()
                .anyMatch(e -> e.getMessage() != null && e.getMessage().contains("Deadlock"));

        if (raceConditionDetected) {
            System.out.println("⚠️ Race Condition 발생 확인!");
            System.out.println("   최대 인원(" + finalMeetup.getMaxParticipants() +
                    ")을 초과하여 " + finalMeetup.getCurrentParticipants() + "명이 참가됨");
        } else if (deadlockOccurred) {
            System.out.println("⚠️ Deadlock 발생 (MySQL의 REPEATABLE READ 격리 수준으로 인한 동시성 제어)");
            System.out.println("   Deadlock으로 인해 일부 트랜잭션이 롤백되어 Race Condition이 방지됨");
            System.out.println("   하지만 이는 의도한 동시성 제어가 아니라 DB 레벨의 부수 효과임");
            System.out.println("   실제 운영 환경에서는 명시적인 Lock 메커니즘 필요");
        } else {
            System.out.println("✅ Race Condition 미발생 (정상)");
        }

        // 예외 로그 출력
        if (!exceptions.isEmpty()) {
            System.out.println("\n=== 발생한 예외들 ===");
            exceptions.forEach(e -> {
                String msg = e.getMessage();
                if (msg != null && msg.contains("Deadlock")) {
                    System.out.println("  - " + e.getClass().getSimpleName() + ": Deadlock 발생");
                    System.out.println("    → MySQL의 REPEATABLE READ 격리 수준에서 동시 UPDATE 시 발생");
                    System.out.println("    → 이는 Race Condition을 우연히 방지하지만, 의도한 동시성 제어는 아님");
                } else {
                    System.out.println("  - " + e.getClass().getSimpleName() + ": " + msg);
                }
            });
            System.out.println("==================\n");
        }

        // 검증: 인원 초과 여부 확인
        // Deadlock이 발생해도 최종 결과는 정상이어야 함
        assertTrue(finalMeetup.getCurrentParticipants() <= finalMeetup.getMaxParticipants(),
                String.format("인원 초과: 최대 인원(%d)을 초과하여 %d명이 참가됨. " +
                        "Deadlock 발생 시에도 최종 상태는 정상이어야 함",
                        finalMeetup.getMaxParticipants(), finalMeetup.getCurrentParticipants()));

        // 검증: 실제 참가자 수와 currentParticipants 일치 확인
        assertEquals(actualParticipantCount, (long) finalMeetup.getCurrentParticipants(),
                "실제 참가자 수와 currentParticipants가 일치해야 함");

        // Deadlock 발생 시 경고 메시지
        if (deadlockOccurred && !raceConditionDetected) {
            System.out.println("\n=== 중요 ===");
            System.out.println("Deadlock이 발생했지만 Race Condition은 발생하지 않았습니다.");
            System.out.println("이는 MySQL의 REPEATABLE READ 격리 수준과 트랜잭션 Lock 때문입니다.");
            System.out.println("하지만 Deadlock은 예측 불가능하고 성능에 악영향을 줍니다.");
            System.out.println("명시적인 Pessimistic Lock 또는 원자적 업데이트 쿼리 사용을 권장합니다.");
            System.out.println("==========\n");
        }
    }

    @Test
    @DisplayName("Race Condition 재현 - 트랜잭션 없이 직접 Repository 사용 (가장 확실한 재현)")
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testRaceConditionWithoutTransaction() throws InterruptedException {
        Long meetupIdx = testMeetup.getIdx();
        int attemptCount = 3; // 동시 참가 시도 인원

        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch startLatch = new CountDownLatch(attemptCount);
        CountDownLatch readyLatch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<String> joinLogs = Collections.synchronizedList(new ArrayList<>());

        System.out.println("\n=== Race Condition 테스트 (트랜잭션 없이) ===");
        System.out.println("트랜잭션 없이 직접 Repository 사용");
        System.out.println("동시 참가 시도: " + attemptCount + "명");
        System.out.println("예상 결과: Race Condition 발생하여 인원 초과");
        System.out.println("==========================================\n");

        // 동시에 3명이 참가 시도 (트랜잭션 없이)
        for (int i = 0; i < attemptCount; i++) {
            final int userIndex = i;
            final Users user = participants.get(i);
            final Long userIdx = user.getIdx();

            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    readyLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    long startTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    System.out.println("[시작] " + threadName + " - 사용자 " + userIndex + " 참가 시도 시작");
                    joinLogs.add(String.format("[%s] 사용자%d 참가 시도 시작",
                            LocalDateTime.now().toString(), userIndex));

                    // 트랜잭션 없이 직접 Repository 사용 (Race Condition 재현)
                    Meetup meetup = meetupRepository.findById(meetupIdx)
                            .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

                    // 인원 체크
                    int currentParticipants = meetup.getCurrentParticipants();
                    int maxParticipants = meetup.getMaxParticipants();

                    System.out.println(String.format("[체크] %s - 사용자%d 체크 - 현재 인원: %d/%d",
                            threadName, userIndex, currentParticipants, maxParticipants));
                    joinLogs.add(String.format("[%s] 사용자%d 체크 - 현재 인원: %d/%d",
                            LocalDateTime.now().toString(), userIndex, currentParticipants, maxParticipants));

                    // ⚠️ Race Condition 발생 지점: 체크와 업데이트 사이에 딜레이
                    Thread.sleep(50); // 50ms 딜레이로 다른 트랜잭션이 끼어들 수 있게 함

                    if (currentParticipants >= maxParticipants) {
                        throw new RuntimeException("모임 인원이 가득 찼습니다.");
                    }

                    // 이미 참가했는지 확인
                    if (meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx)) {
                        throw new RuntimeException("이미 참가한 모임입니다.");
                    }

                    // 참가자 추가
                    MeetupParticipants participant = MeetupParticipants.builder()
                            .meetup(meetup)
                            .user(user)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    meetupParticipantsRepository.save(participant);

                    // 인원 증가 (⚠️ Race Condition 발생!)
                    meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
                    meetupRepository.save(meetup);

                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    // 참가 후 상태 확인
                    Meetup afterMeetup = meetupRepository.findById(meetupIdx).orElse(null);
                    if (afterMeetup != null) {
                        System.out.println(String.format("[성공] %s - 사용자 %d 참가 성공 (소요시간: %dms) - 현재 인원: %d/%d",
                                threadName, userIndex, duration,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                        joinLogs.add(String.format("[%s] 사용자%d 참가 성공 - 현재 인원: %d/%d",
                                LocalDateTime.now().toString(), userIndex,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                    }

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    long endTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    String errorMsg = e.getMessage();
                    System.out.println(String.format("[실패] %s - 사용자 %d 참가 실패: %s",
                            threadName, userIndex, errorMsg));
                    joinLogs.add(String.format("[%s] 사용자%d 참가 실패: %s",
                            LocalDateTime.now().toString(), userIndex, errorMsg));

                    exceptions.add(e);
                    failureCount.incrementAndGet();
                }
            });

            readyLatch.countDown();
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!terminated) {
            System.out.println("경고: 일부 스레드가 10초 내에 완료되지 않았습니다.");
            executor.shutdownNow();
        }

        // 최종 상태 확인
        Meetup finalMeetup = meetupRepository.findById(meetupIdx).orElse(null);
        assertNotNull(finalMeetup, "모임이 존재해야 함");

        long actualParticipantCount = meetupParticipantsRepository.countByMeetupIdx(meetupIdx);

        System.out.println("\n=== 테스트 결과 ===");
        System.out.println("성공한 참가: " + successCount.get() + "명");
        System.out.println("실패한 참가: " + failureCount.get() + "명");
        System.out.println("최종 currentParticipants: " + finalMeetup.getCurrentParticipants());
        System.out.println("실제 참가자 수 (DB): " + actualParticipantCount);
        System.out.println("최대 인원: " + finalMeetup.getMaxParticipants());
        System.out.println("==================\n");

        // 상세 로그 출력
        System.out.println("=== 상세 로그 ===");
        joinLogs.forEach(System.out::println);
        System.out.println("================\n");

        // 검증: Race Condition 발생 여부 확인
        // 1. Lost Update 감지: 실제 참가자 수와 currentParticipants가 일치하지 않는 경우
        boolean lostUpdateDetected = (int) actualParticipantCount != finalMeetup.getCurrentParticipants();

        // 2. 인원 초과 감지: 실제 참가자 수가 최대 인원을 초과하는 경우
        boolean overCapacityDetected = actualParticipantCount > finalMeetup.getMaxParticipants();

        // 3. 저장된 값이 최대 인원을 초과하는 경우
        boolean storedValueOverCapacity = finalMeetup.getCurrentParticipants() > finalMeetup.getMaxParticipants();

        // Race Condition 발생 여부 (위 세 가지 중 하나라도 발생하면)
        boolean raceConditionDetected = lostUpdateDetected || overCapacityDetected || storedValueOverCapacity;

        System.out.println("\n=== Race Condition 분석 ===");
        System.out.println("Lost Update 발생: " + (lostUpdateDetected ? "✅ YES" : "❌ NO"));
        if (lostUpdateDetected) {
            System.out.println("   → 실제 참가자 수(" + actualParticipantCount +
                    ")와 저장된 currentParticipants(" + finalMeetup.getCurrentParticipants() + ") 불일치");
            System.out.println("   → 여러 트랜잭션이 동시에 읽고 업데이트하여 마지막 값만 저장됨");
        }

        System.out.println("인원 초과 발생: " + (overCapacityDetected ? "✅ YES" : "❌ NO"));
        if (overCapacityDetected) {
            System.out.println("   → 실제 참가자 수(" + actualParticipantCount +
                    ")가 최대 인원(" + finalMeetup.getMaxParticipants() + ")을 초과");
        }

        System.out.println("저장값 초과: " + (storedValueOverCapacity ? "✅ YES" : "❌ NO"));
        if (storedValueOverCapacity) {
            System.out.println("   → 저장된 currentParticipants(" + finalMeetup.getCurrentParticipants() +
                    ")가 최대 인원(" + finalMeetup.getMaxParticipants() + ")을 초과");
        }
        System.out.println("========================\n");

        if (raceConditionDetected) {
            System.out.println("⚠️ Race Condition 발생 확인!");
            System.out.println("   ✅ Race Condition이 실제로 발생했습니다!");
            System.out.println("   → 이제 해결 방법을 적용해야 합니다.");
        } else {
            System.out.println("✅ Race Condition 미발생");
        }

        // 예외 로그 출력
        if (!exceptions.isEmpty()) {
            System.out.println("\n=== 발생한 예외들 ===");
            exceptions.forEach(e -> System.out.println("  - " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            System.out.println("==================\n");
        }
    }

    @Test
    @DisplayName("✅ 해결 후 - Pessimistic Lock 적용된 서비스 메서드 사용")
    void testRaceConditionFixedWithPessimisticLock() throws InterruptedException {
        Long meetupIdx = testMeetup.getIdx();
        int attemptCount = 3; // 동시 참가 시도 인원

        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch startLatch = new CountDownLatch(attemptCount);
        CountDownLatch readyLatch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<String> joinLogs = Collections.synchronizedList(new ArrayList<>());

        System.out.println("\n=== ✅ 해결 후 테스트 (Pessimistic Lock 적용) ===");
        System.out.println("서비스 메서드 사용 (Lock 적용됨)");
        System.out.println("동시 참가 시도: " + attemptCount + "명");
        System.out.println("예상 결과: 2명 성공, 1명 실패 (최대 인원 3명)");
        System.out.println("===============================================\n");

        // 동시에 3명이 참가 시도 (서비스 메서드 사용)
        for (int i = 0; i < attemptCount; i++) {
            final int userIndex = i;
            final Users user = participants.get(i);
            final String userId = user.getId();

            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    readyLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    long startTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    System.out.println("[시작] " + threadName + " - 사용자 " + userIndex + " 참가 시도 시작");
                    joinLogs.add(String.format("[%s] 사용자%d 참가 시도 시작",
                            LocalDateTime.now().toString(), userIndex));

                    // ✅ 서비스 메서드 사용 (Pessimistic Lock 적용됨)
                    meetupService.joinMeetup(meetupIdx, userId);

                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    // 참가 후 상태 확인
                    Meetup afterMeetup = meetupRepository.findById(meetupIdx).orElse(null);
                    if (afterMeetup != null) {
                        System.out.println(String.format("[성공] %s - 사용자 %d 참가 성공 (소요시간: %dms) - 현재 인원: %d/%d",
                                threadName, userIndex, duration,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                        joinLogs.add(String.format("[%s] 사용자%d 참가 성공 - 현재 인원: %d/%d",
                                LocalDateTime.now().toString(), userIndex,
                                afterMeetup.getCurrentParticipants(), afterMeetup.getMaxParticipants()));
                    }

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    long endTime = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    String errorMsg = e.getMessage();
                    System.out.println(String.format("[실패] %s - 사용자 %d 참가 실패: %s",
                            threadName, userIndex, errorMsg));
                    joinLogs.add(String.format("[%s] 사용자%d 참가 실패: %s",
                            LocalDateTime.now().toString(), userIndex, errorMsg));

                    exceptions.add(e);
                    failureCount.incrementAndGet();
                }
            });

            readyLatch.countDown();
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!terminated) {
            System.out.println("경고: 일부 스레드가 10초 내에 완료되지 않았습니다.");
            executor.shutdownNow();
        }

        // 최종 상태 확인
        Meetup finalMeetup = meetupRepository.findById(meetupIdx).orElse(null);
        assertNotNull(finalMeetup, "모임이 존재해야 함");

        long actualParticipantCount = meetupParticipantsRepository.countByMeetupIdx(meetupIdx);

        System.out.println("\n=== ✅ 해결 후 테스트 결과 ===");
        System.out.println("성공한 참가: " + successCount.get() + "명");
        System.out.println("실패한 참가: " + failureCount.get() + "명");
        System.out.println("최종 currentParticipants: " + finalMeetup.getCurrentParticipants());
        System.out.println("실제 참가자 수 (DB): " + actualParticipantCount);
        System.out.println("최대 인원: " + finalMeetup.getMaxParticipants());
        System.out.println("===========================\n");

        // 상세 로그 출력
        System.out.println("=== 상세 로그 ===");
        joinLogs.forEach(System.out::println);
        System.out.println("================\n");

        // 검증: Race Condition 발생 여부 확인
        // 1. Lost Update 감지: 실제 참가자 수와 currentParticipants가 일치하지 않는 경우
        boolean lostUpdateDetected = (int) actualParticipantCount != finalMeetup.getCurrentParticipants();

        // 2. 인원 초과 감지: 실제 참가자 수가 최대 인원을 초과하는 경우
        boolean overCapacityDetected = actualParticipantCount > finalMeetup.getMaxParticipants();

        // 3. 저장된 값이 최대 인원을 초과하는 경우
        boolean storedValueOverCapacity = finalMeetup.getCurrentParticipants() > finalMeetup.getMaxParticipants();

        // Race Condition 발생 여부 (위 세 가지 중 하나라도 발생하면)
        boolean raceConditionDetected = lostUpdateDetected || overCapacityDetected || storedValueOverCapacity;

        System.out.println("\n=== ✅ 해결 후 Race Condition 분석 ===");
        System.out.println("Lost Update 발생: " + (lostUpdateDetected ? "❌ YES (문제!)" : "✅ NO"));
        if (lostUpdateDetected) {
            System.out.println("   → 실제 참가자 수(" + actualParticipantCount +
                    ")와 저장된 currentParticipants(" + finalMeetup.getCurrentParticipants() + ") 불일치");
        } else {
            System.out.println("   → ✅ Lost Update 해결됨!");
        }

        System.out.println("인원 초과 발생: " + (overCapacityDetected ? "❌ YES (문제!)" : "✅ NO"));
        if (overCapacityDetected) {
            System.out.println("   → 실제 참가자 수(" + actualParticipantCount +
                    ")가 최대 인원(" + finalMeetup.getMaxParticipants() + ")을 초과");
        } else {
            System.out.println("   → ✅ 인원 초과 해결됨!");
        }

        System.out.println("저장값 초과: " + (storedValueOverCapacity ? "❌ YES (문제!)" : "✅ NO"));
        if (storedValueOverCapacity) {
            System.out.println("   → 저장된 currentParticipants(" + finalMeetup.getCurrentParticipants() +
                    ")가 최대 인원(" + finalMeetup.getMaxParticipants() + ")을 초과");
        }
        System.out.println("===================================\n");

        if (!raceConditionDetected) {
            System.out.println("✅ Race Condition 해결 확인!");
            System.out.println("   → Pessimistic Lock이 정상적으로 작동하여 Race Condition이 발생하지 않았습니다.");
            System.out.println("   → 순차적으로 처리되어 인원 제한이 정확히 적용되었습니다.");
        } else {
            System.out.println("⚠️ Race Condition이 여전히 발생했습니다!");
            System.out.println("   → Lock 적용을 확인해주세요.");
        }

        // 예외 로그 출력
        if (!exceptions.isEmpty()) {
            System.out.println("\n=== 발생한 예외들 ===");
            exceptions.forEach(e -> System.out.println("  - " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            System.out.println("==================\n");
        }

        // 검증: Race Condition이 발생하지 않았는지 확인
        assertFalse(raceConditionDetected,
                "Pessimistic Lock 적용 후 Race Condition이 발생하지 않아야 함");
        assertEquals(actualParticipantCount, (long) finalMeetup.getCurrentParticipants(),
                "실제 참가자 수와 currentParticipants가 일치해야 함");
        assertTrue(actualParticipantCount <= finalMeetup.getMaxParticipants(),
                "실제 참가자 수가 최대 인원을 초과하지 않아야 함");
    }

    @Test
    @DisplayName("Race Condition 재현 - 더 많은 동시 참가 시도 (5명)")
    void testRaceConditionWithMoreUsers() throws InterruptedException {
        // 추가 사용자 2명 생성
        long timestamp = System.currentTimeMillis();
        for (int i = 3; i < 5; i++) {
            Users participant = Users.builder()
                    .id("participant_" + timestamp + "_" + i)
                    .username("participant_" + timestamp + "_" + i)
                    .email("participant_" + timestamp + "_" + i + "@example.com")
                    .password("password")
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .build();
            participant = usersRepository.save(participant);
            participants.add(participant);
        }

        Long meetupIdx = testMeetup.getIdx();
        int attemptCount = 5; // 동시 참가 시도 인원

        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch startLatch = new CountDownLatch(attemptCount);
        CountDownLatch readyLatch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> timeline = Collections.synchronizedList(new ArrayList<>());

        System.out.println("\n=== Race Condition 테스트 (5명 동시 시도) ===");
        System.out.println("최대 인원: " + testMeetup.getMaxParticipants());
        System.out.println("현재 인원: " + testMeetup.getCurrentParticipants());
        System.out.println("동시 참가 시도: " + attemptCount + "명");
        System.out.println("예상 결과: 2명 성공, 3명 실패");
        System.out.println("==========================================\n");

        for (int i = 0; i < attemptCount; i++) {
            final int userIndex = i;
            final Users user = participants.get(i);
            final String userId = user.getId();

            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    readyLatch.await();

                    long timestamp_ms = System.currentTimeMillis();
                    timeline.add(String.format("[%d] 사용자%d 참가 시도 시작", timestamp_ms, userIndex));

                    Meetup beforeMeetup = meetupRepository.findById(meetupIdx).orElse(null);
                    if (beforeMeetup != null) {
                        timeline.add(String.format("[%d] 사용자%d 체크 - 현재 인원: %d/%d",
                                timestamp_ms, userIndex, beforeMeetup.getCurrentParticipants(),
                                beforeMeetup.getMaxParticipants()));
                    }

                    meetupService.joinMeetup(meetupIdx, userId);

                    timestamp_ms = System.currentTimeMillis();
                    timeline.add(String.format("[%d] 사용자%d 참가 성공", timestamp_ms, userIndex));
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    long timestamp_ms = System.currentTimeMillis();
                    timeline.add(String.format("[%d] 사용자%d 참가 실패: %s",
                            timestamp_ms, userIndex, e.getMessage()));
                }
            });

            readyLatch.countDown();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Meetup finalMeetup = meetupRepository.findById(meetupIdx).orElse(null);
        assertNotNull(finalMeetup);

        System.out.println("\n=== 타임라인 (시간순 정렬) ===");
        timeline.stream()
                .sorted()
                .forEach(System.out::println);
        System.out.println("===========================\n");

        System.out.println("성공한 참가: " + successCount.get() + "명");
        System.out.println("최종 인원: " + finalMeetup.getCurrentParticipants() + "/" + finalMeetup.getMaxParticipants());

        // Race Condition 검증
        assertTrue(finalMeetup.getCurrentParticipants() <= finalMeetup.getMaxParticipants(),
                "Race Condition 발생: 최대 인원 초과");
    }

    @Test
    @DisplayName("성능 비교: Pessimistic Lock vs 원자적 UPDATE 쿼리")
    void testPerformanceComparison() throws InterruptedException {
        System.out.println("\n=== 성능 비교 테스트 시작 ===");
        System.out.println("비교 방식: Pessimistic Lock vs 원자적 UPDATE 쿼리");
        System.out.println("동시 참가 시도: 10명");
        System.out.println("테스트 횟수: 각 방식당 5회");
        System.out.println("================================\n");

        int attemptCount = 10;
        int testRuns = 5;

        // Pessimistic Lock 방식 성능 측정 (레거시 방식 - 직접 구현)
        List<Long> pessimisticLockTimes = new ArrayList<>();
        List<Long> atomicUpdateTimes = new ArrayList<>();

        for (int run = 0; run < testRuns; run++) {
            // 테스트 데이터 재설정
            setUp();
            Long meetupIdx = testMeetup.getIdx();

            // 추가 사용자 생성 (10명)
            long timestamp = System.currentTimeMillis();
            List<Users> testUsers = new ArrayList<>();
            for (int i = 0; i < attemptCount; i++) {
                Users user = Users.builder()
                        .id("test_user_" + timestamp + "_" + i)
                        .username("test_user_" + timestamp + "_" + i)
                        .email("test_user_" + timestamp + "_" + i + "@example.com")
                        .password("password")
                        .role(Role.USER)
                        .status(UserStatus.ACTIVE)
                        .emailVerified(true)
                        .build();
                user = usersRepository.save(user);
                testUsers.add(user);
            }

            // Pessimistic Lock 방식 테스트
            long pessimisticStart = System.currentTimeMillis();
            testPessimisticLockApproach(meetupIdx, testUsers);
            long pessimisticEnd = System.currentTimeMillis();
            pessimisticLockTimes.add(pessimisticEnd - pessimisticStart);

            // 테스트 데이터 재설정
            setUp();
            meetupIdx = testMeetup.getIdx();

            // 원자적 UPDATE 쿼리 방식 테스트 (현재 구현)
            long atomicStart = System.currentTimeMillis();
            testAtomicUpdateApproach(meetupIdx, testUsers);
            long atomicEnd = System.currentTimeMillis();
            atomicUpdateTimes.add(atomicEnd - atomicStart);

            System.out.println(String.format("테스트 %d회 완료 - Pessimistic Lock: %dms, 원자적 UPDATE: %dms",
                    run + 1, pessimisticEnd - pessimisticStart, atomicEnd - atomicStart));
        }

        // 통계 계산
        double pessimisticAvg = pessimisticLockTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double atomicAvg = atomicUpdateTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long pessimisticMin = pessimisticLockTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long atomicMin = atomicUpdateTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long pessimisticMax = pessimisticLockTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long atomicMax = atomicUpdateTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println("\n=== 성능 비교 결과 ===");
        System.out.println("Pessimistic Lock 방식:");
        System.out.println("  평균: " + String.format("%.2f", pessimisticAvg) + "ms");
        System.out.println("  최소: " + pessimisticMin + "ms");
        System.out.println("  최대: " + pessimisticMax + "ms");
        System.out.println("\n원자적 UPDATE 쿼리 방식:");
        System.out.println("  평균: " + String.format("%.2f", atomicAvg) + "ms");
        System.out.println("  최소: " + atomicMin + "ms");
        System.out.println("  최대: " + atomicMax + "ms");

        double improvement = ((pessimisticAvg - atomicAvg) / pessimisticAvg) * 100;
        System.out.println("\n성능 개선율: " + String.format("%.2f", improvement) + "%");
        System.out.println("========================\n");

        // 검증: 원자적 UPDATE 쿼리 방식이 더 빠르거나 비슷해야 함
        assertTrue(atomicAvg <= pessimisticAvg * 1.1,
                "원자적 UPDATE 쿼리 방식이 Pessimistic Lock보다 느리면 안 됨");
    }

    private void testPessimisticLockApproach(Long meetupIdx, List<Users> testUsers) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(testUsers.size());
        CountDownLatch readyLatch = new CountDownLatch(testUsers.size());
        AtomicInteger successCount = new AtomicInteger(0);

        for (Users user : testUsers) {
            final String userId = user.getId();
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    // Pessimistic Lock 방식 (레거시)
                    Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
                            .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

                    Long userIdx = user.getIdx();
                    if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
                        if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
                            return;
                        }
                        meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
                        meetupRepository.save(meetup);
                    }

                    MeetupParticipants participant = MeetupParticipants.builder()
                            .meetup(meetup)
                            .user(user)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    meetupParticipantsRepository.save(participant);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 무시
                }
            });
            readyLatch.countDown();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void testAtomicUpdateApproach(Long meetupIdx, List<Users> testUsers) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(testUsers.size());
        CountDownLatch readyLatch = new CountDownLatch(testUsers.size());
        AtomicInteger successCount = new AtomicInteger(0);

        for (Users user : testUsers) {
            final String userId = user.getId();
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    // 원자적 UPDATE 쿼리 방식 (현재 구현)
                    // 주최자 확인을 위해 한 번만 조회
                    Meetup meetup = meetupRepository.findById(meetupIdx)
                            .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

                    Long userIdx = user.getIdx();
                    if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
                        // 원자적 UPDATE 쿼리 실행
                        int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
                        if (updated == 0) {
                            return;
                        }
                        // 참가자 추가를 위해 업데이트된 모임 정보 다시 조회 (실제 서비스 로직과 동일)
                        meetup = meetupRepository.findById(meetupIdx)
                                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
                    }

                    MeetupParticipants participant = MeetupParticipants.builder()
                            .meetup(meetup)
                            .user(user)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    meetupParticipantsRepository.save(participant);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 무시
                }
            });
            readyLatch.countDown();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
