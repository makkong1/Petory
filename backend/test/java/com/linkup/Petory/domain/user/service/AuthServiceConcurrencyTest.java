package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.dto.TokenResponse;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.util.JwtUtil;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthService 동시성 테스트
 * 
 * 문제 시나리오:
 * 1. Refresh Token 동시 갱신 문제
 * - 여러 기기에서 동시에 Refresh Token으로 Access Token 갱신 시도
 * - 예상 증상: Refresh Token 불일치, 로그아웃 처리
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceConcurrencyTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Users testUser;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성
        testUser = Users.builder()
                .id("test_user")
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                .status(com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE)
                .warningCount(0)
                .build();
        testUser = usersRepository.save(testUser);

        // Refresh Token 생성 및 저장
        refreshToken = jwtUtil.createRefreshToken();
        testUser.setRefreshToken(refreshToken);
        testUser.setRefreshExpiration(LocalDateTime.now().plusDays(1));
        testUser = usersRepository.save(testUser);
    }

    @Test
    @DisplayName("Refresh Token 동시 갱신 시도 - 여러 기기에서 동시에 갱신")
    void testConcurrentRefreshTokenRenewal() throws InterruptedException {
        int threadCount = 10; // 동시에 10개 기기에서 갱신 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> refreshTokens = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();

        // 동시에 여러 기기에서 Refresh Token 갱신 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // 모든 스레드가 준비될 때까지 대기

                    TokenResponse response = authService.refreshAccessToken(refreshToken);
                    successCount.incrementAndGet();
                    refreshTokens.add(response.getRefreshToken());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("실패 횟수: " + failureCount.get());

        // 동시 갱신 시도 시 문제 확인
        // 이상적으로는 1개만 성공하고 나머지는 실패해야 함
        // 또는 모두 성공하더라도 Refresh Token이 일치해야 함

        // DB에서 최종 Refresh Token 확인
        Users finalUser = usersRepository.findById(testUser.getIdx()).orElse(null);
        assertNotNull(finalUser, "사용자가 존재해야 함");

        // 성공한 경우 Refresh Token이 유지되어야 함
        if (successCount.get() > 0) {
            assertNotNull(finalUser.getRefreshToken(), "Refresh Token이 유지되어야 함");
        }
    }

    @Test
    @DisplayName("Refresh Token 동시 갱신 - Lost Update 문제 확인")
    void testConcurrentRefreshTokenLostUpdate() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicReference<String> lastRefreshToken = new AtomicReference<>(refreshToken);
        List<String> allRefreshTokens = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    String currentToken = lastRefreshToken.get();
                    TokenResponse response = authService.refreshAccessToken(currentToken);
                    lastRefreshToken.set(response.getRefreshToken());
                    allRefreshTokens.add(response.getRefreshToken());

                    System.out.println(
                            "Thread " + threadId + " 성공: " + response.getAccessToken().substring(0, 20) + "...");
                } catch (Exception e) {
                    System.out.println("Thread " + threadId + " 실패: " + e.getMessage());
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

        System.out.println("최종 Refresh Token: " + finalUser.getRefreshToken());
        System.out.println("성공한 Refresh Token 개수: " + allRefreshTokens.size());
    }
}
