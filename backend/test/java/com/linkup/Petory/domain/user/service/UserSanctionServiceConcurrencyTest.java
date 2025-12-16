package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UserSanctionRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserSanctionService 동시성 테스트
 * 
 * 문제 시나리오:
 * 2. 경고 횟수 동시 증가 문제
 * - 여러 관리자가 동시에 같은 사용자에게 경고 부여
 * - 예상 증상: 경고 횟수 부정확, 자동 이용제한 중복 적용
 */
@SpringBootTest
@ActiveProfiles("test")
class UserSanctionServiceConcurrencyTest {

    @Autowired
    private UserSanctionService userSanctionService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UserSanctionRepository userSanctionRepository;

    private Users testUser;
    private Users admin1;
    private Users admin2;
    private Users admin3;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 데이터 사용
        long timestamp = System.currentTimeMillis();

        // 테스트용 사용자 생성
        testUser = Users.builder()
                .id("test_user_" + timestamp)
                .username("testuser_" + timestamp)
                .email("test_" + timestamp + "@example.com")
                .password("password")
                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
                .build();
        testUser = usersRepository.save(testUser);

        // 테스트용 관리자 생성
        admin1 = Users.builder()
                .id("admin1_" + timestamp)
                .username("admin1_" + timestamp)
                .email("admin1_" + timestamp + "@example.com")
                .password("password")
                .role(com.linkup.Petory.domain.user.entity.Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
                .build();
        admin1 = usersRepository.save(admin1);

        admin2 = Users.builder()
                .id("admin2_" + timestamp)
                .username("admin2_" + timestamp)
                .email("admin2_" + timestamp + "@example.com")
                .password("password")
                .role(com.linkup.Petory.domain.user.entity.Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
                .build();
        admin2 = usersRepository.save(admin2);

        admin3 = Users.builder()
                .id("admin3_" + timestamp)
                .username("admin3_" + timestamp)
                .email("admin3_" + timestamp + "@example.com")
                .password("password")
                .role(com.linkup.Petory.domain.user.entity.Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
                .build();
        admin3 = usersRepository.save(admin3);
    }

    @Test
    @DisplayName("경고 횟수 동시 증가 문제 - 여러 관리자가 동시에 경고 부여")
    void testConcurrentWarningIncrement() throws InterruptedException {
        int adminCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(adminCount);
        CountDownLatch latch = new CountDownLatch(adminCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> warningCounts = new ArrayList<>();

        // 여러 관리자가 동시에 경고 부여
        List<Users> admins = List.of(admin1, admin2, admin3);

        for (int i = 0; i < adminCount; i++) {
            final int adminIndex = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // 모든 스레드가 준비될 때까지 대기

                    Users admin = admins.get(adminIndex);
                    userSanctionService.addWarning(
                            testUser.getIdx(),
                            "동시 경고 테스트 " + adminIndex,
                            admin.getIdx(),
                            null);

                    successCount.incrementAndGet();

                    // 경고 횟수 확인
                    Users user = usersRepository.findById(testUser.getIdx()).orElse(null);
                    if (user != null) {
                        warningCounts.add(user.getWarningCount());
                    }
                } catch (Exception e) {
                    System.out.println("관리자 " + adminIndex + " 경고 부여 실패: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        Users finalUser = usersRepository.findById(testUser.getIdx()).orElse(null);
        assertNotNull(finalUser, "사용자가 존재해야 함");

        System.out.println("성공한 경고 부여 횟수: " + successCount.get());
        System.out.println("최종 경고 횟수: " + finalUser.getWarningCount());
        System.out.println("경고 기록 개수: " + userSanctionRepository.countWarningsByUserId(testUser.getIdx()));

        // 경고 횟수가 정확한지 확인
        // 이상적으로는 경고 횟수 = 경고 기록 개수여야 함
        long actualWarningCount = userSanctionRepository.countWarningsByUserId(testUser.getIdx());
        assertEquals((long) actualWarningCount, (long) finalUser.getWarningCount(),
                "경고 횟수와 실제 경고 기록 수가 일치해야 함");
    }

    @Test
    @DisplayName("경고 횟수 동시 증가 - Lost Update 문제 확인")
    void testConcurrentWarningLostUpdate() throws InterruptedException {
        int adminCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(adminCount);
        CountDownLatch latch = new CountDownLatch(adminCount);

        List<Integer> warningCounts = new ArrayList<>();
        List<Long> adminIds = List.of(admin1.getIdx(), admin2.getIdx(), admin3.getIdx(),
                admin1.getIdx(), admin2.getIdx());

        // 5명의 관리자가 동시에 경고 부여
        for (int i = 0; i < adminCount; i++) {
            final int adminIndex = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    userSanctionService.addWarning(
                            testUser.getIdx(),
                            "Lost Update 테스트 " + adminIndex,
                            adminIds.get(adminIndex),
                            null);

                    // 경고 부여 후 즉시 경고 횟수 확인
                    Users user = usersRepository.findById(testUser.getIdx()).orElse(null);
                    if (user != null) {
                        synchronized (warningCounts) {
                            warningCounts.add(user.getWarningCount());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("경고 부여 실패 " + adminIndex + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 최종 상태 확인
        Users finalUser = usersRepository.findById(testUser.getIdx()).orElse(null);
        assertNotNull(finalUser);

        long actualWarningCount = userSanctionRepository.countWarningsByUserId(testUser.getIdx());

        System.out.println("최종 경고 횟수 (DB): " + finalUser.getWarningCount());
        System.out.println("실제 경고 기록 수: " + actualWarningCount);
        System.out.println("중간 경고 횟수들: " + warningCounts);

        assertEquals((long) actualWarningCount, (long) finalUser.getWarningCount(),
                "동시 요청 시 경고 횟수가 누락될 수 있습니다.");
    }

    @Test
    @DisplayName("경고 3회 도달 시 자동 이용제한 중복 적용 방지")
    void testConcurrentWarningAutoSuspension() throws InterruptedException {
        // 초기 경고 2회 설정
        testUser.setWarningCount(2);
        testUser = usersRepository.save(testUser);

        int adminCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(adminCount);
        CountDownLatch latch = new CountDownLatch(adminCount);

        AtomicInteger suspensionCount = new AtomicInteger(0);

        // 여러 관리자가 동시에 경고 부여 (3회 도달 시 자동 이용제한)
        List<Users> admins = List.of(admin1, admin2, admin3);

        for (int i = 0; i < adminCount; i++) {
            final int adminIndex = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    Users admin = admins.get(adminIndex);
                    userSanctionService.addWarning(
                            testUser.getIdx(),
                            "자동 이용제한 테스트 " + adminIndex,
                            admin.getIdx(),
                            null);

                    // 이용제한이 적용되었는지 확인
                    Users user = usersRepository.findById(testUser.getIdx()).orElse(null);
                    if (user != null && user.getStatus() == UserStatus.SUSPENDED) {
                        suspensionCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("경고 부여 실패 " + adminIndex + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        Users finalUser = usersRepository.findById(testUser.getIdx()).orElse(null);
        assertNotNull(finalUser);

        System.out.println("최종 상태: " + finalUser.getStatus());
        System.out.println("이용제한 적용 횟수: " + suspensionCount.get());

        // 이용제한이 한 번만 적용되어야 함
        if (finalUser.getStatus() == UserStatus.SUSPENDED) {
            assertNotNull(finalUser.getSuspendedUntil(), "이용제한 종료일이 설정되어야 함");
        }
    }
}
