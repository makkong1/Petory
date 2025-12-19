package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.entity.Provider;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.SocialUserRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth2Service 동시성 테스트
 * 
 * 문제 시나리오:
 * 3. 소셜 로그인 시 중복 계정 생성
 * - 소셜 로그인 시 기존 계정 확인 로직 누락
 * - 같은 소셜 계정으로 여러 번 로그인
 * - 예상 증상: 중복 계정 생성
 */
@SpringBootTest
@ActiveProfiles("test")
class OAuth2ServiceConcurrencyTest {

    @Autowired
    private OAuth2Service oAuth2Service;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private SocialUserRepository socialUserRepository;

    private String testProviderId;
    private String testEmail;
    private Provider testProvider;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 데이터 사용
        long timestamp = System.currentTimeMillis();
        testProviderId = "test_oauth_provider_id_" + timestamp;
        testEmail = "test_oauth_" + timestamp + "@example.com";
        testProvider = Provider.GOOGLE;

        // 기존 데이터 정리
        usersRepository.findByEmail(testEmail).ifPresent(usersRepository::delete);
        socialUserRepository.findAll().stream()
                .filter(su -> testProvider.equals(su.getProvider()) &&
                        testProviderId.equals(su.getProviderId()))
                .forEach(socialUserRepository::delete);
    }

    private OAuth2User createMockOAuth2User(String providerId, String email, String name) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", providerId); // Google providerId
        attributes.put("email", email);
        attributes.put("name", name);
        attributes.put("email_verified", true);
        attributes.put("picture", "https://example.com/picture.jpg");

        return new DefaultOAuth2User(
                Collections.singletonList(() -> "sub"),
                attributes,
                "sub");
    }

    @Test
    @DisplayName("소셜 로그인 동시 중복 계정 생성 방지")
    void testConcurrentOAuth2LoginPreventDuplicate() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> createdUserIds = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();

        // 동시에 같은 소셜 계정으로 여러 번 로그인 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // 모든 스레드가 준비될 때까지 대기

                    OAuth2User oauth2User = createMockOAuth2User(
                            testProviderId,
                            testEmail,
                            "Test User " + Thread.currentThread().getId());

                    oAuth2Service.processOAuth2Login(oauth2User, testProvider);
                    successCount.incrementAndGet();

                    // 생성된 사용자 ID 확인
                    Users user = usersRepository.findByEmail(testEmail).orElse(null);
                    if (user != null) {
                        synchronized (createdUserIds) {
                            createdUserIds.add(user.getIdx());
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                    System.out.println("소셜 로그인 실패: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("예외 횟수: " + exceptions.size());
        System.out.println("생성된 사용자 ID들: " + createdUserIds);

        // 같은 이메일로 생성된 사용자가 1명만 있어야 함
        List<Users> usersWithEmail = usersRepository.findAll().stream()
                .filter(u -> testEmail.equals(u.getEmail()))
                .collect(Collectors.toList());

        System.out.println("같은 이메일을 가진 사용자 수: " + usersWithEmail.size());

        // 중복 계정이 생성되지 않았는지 확인
        assertEquals(1, usersWithEmail.size(),
                "같은 이메일로는 하나의 계정만 생성되어야 함");

        // SocialUser도 하나만 있어야 함
        long socialUserCount = socialUserRepository.findAll().stream()
                .filter(su -> testProvider.equals(su.getProvider()) &&
                        testProviderId.equals(su.getProviderId()))
                .count();

        assertEquals(1, socialUserCount,
                "같은 Provider와 ProviderId로는 하나의 SocialUser만 생성되어야 함");
    }

    @Test
    @DisplayName("소셜 로그인 동시 처리 - Race Condition 확인")
    void testConcurrentOAuth2LoginRaceCondition() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Set<Long> uniqueUserIds = Collections.synchronizedSet(new HashSet<>());
        List<String> errorMessages = Collections.synchronizedList(new ArrayList<>());

        // 동시에 같은 소셜 계정으로 로그인 시도
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    OAuth2User oauth2User = createMockOAuth2User(
                            testProviderId,
                            testEmail,
                            "Test User");

                    oAuth2Service.processOAuth2Login(oauth2User, testProvider);

                    Users user = usersRepository.findByEmail(testEmail).orElse(null);
                    if (user != null) {
                        uniqueUserIds.add(user.getIdx());
                    }
                } catch (Exception e) {
                    errorMessages.add("Thread " + threadId + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        System.out.println("고유한 사용자 ID 개수: " + uniqueUserIds.size());
        System.out.println("에러 메시지 개수: " + errorMessages.size());
        if (!errorMessages.isEmpty()) {
            System.out.println("에러 메시지들: " + errorMessages);
        }

        // 최종 상태 확인
        List<Users> finalUsers = usersRepository.findAll().stream()
                .filter(u -> testEmail.equals(u.getEmail()))
                .collect(Collectors.toList());

        assertEquals(1, finalUsers.size(),
                "최종적으로 하나의 사용자만 존재해야 함");

        Users finalUser = finalUsers.get(0);
        assertNotNull(finalUser, "사용자가 생성되어야 함");

        // SocialUser 확인
        long socialUserCount = socialUserRepository.findAll().stream()
                .filter(su -> testProvider.equals(su.getProvider()) &&
                        testProviderId.equals(su.getProviderId()))
                .count();

        assertEquals(1, socialUserCount,
                "하나의 SocialUser만 존재해야 함");
    }

    @Test
    @DisplayName("소셜 로그인 - 기존 계정과의 연결 확인")
    void testConcurrentOAuth2LoginExistingAccount() throws InterruptedException {
        // 기존 사용자 생성
        long timestamp = System.currentTimeMillis();
        Users existingUser = Users.builder()
                .id("existing_user_" + timestamp)
                .username("existinguser_" + timestamp)
                .email(testEmail)
                .password("password")
                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                .status(com.linkup.Petory.domain.user.entity.UserStatus.ACTIVE)
                .warningCount(0)
                .build();
        existingUser = usersRepository.save(existingUser);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Set<Long> linkedUserIds = Collections.synchronizedSet(new HashSet<>());

        // 동시에 같은 소셜 계정으로 로그인 시도 (기존 계정과 연결되어야 함)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    OAuth2User oauth2User = createMockOAuth2User(
                            testProviderId,
                            testEmail,
                            "Test User");

                    oAuth2Service.processOAuth2Login(oauth2User, testProvider);

                    Users user = usersRepository.findByEmail(testEmail).orElse(null);
                    if (user != null) {
                        linkedUserIds.add(user.getIdx());
                    }
                } catch (Exception e) {
                    System.out.println("소셜 로그인 실패: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        System.out.println("연결된 사용자 ID들: " + linkedUserIds);

        // 기존 사용자와 연결되어야 함
        assertEquals(1, linkedUserIds.size(),
                "하나의 사용자 ID만 존재해야 함");
        assertTrue(linkedUserIds.contains(existingUser.getIdx()),
                "기존 사용자와 연결되어야 함");

        // SocialUser가 생성되었는지 확인
        final Users finalUser = existingUser;
        long socialUserCount = socialUserRepository.findAll().stream()
                .filter(su -> testProvider.equals(su.getProvider()) &&
                        testProviderId.equals(su.getProviderId()) &&
                        finalUser.getIdx().equals(su.getUser().getIdx()))
                .count();

        assertEquals(1, socialUserCount,
                "기존 사용자와 연결된 SocialUser가 하나만 존재해야 함");
    }
}
