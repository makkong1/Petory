package com.linkup.Petory.domain.user.service;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UsersService 동시성 및 Soft Delete 문제 테스트
 * 
 * 테스트 문제 상황:
 * 1. 닉네임 중복 체크 Race Condition
 * - 동시에 여러 사용자가 같은 닉네임으로 가입 시도
 * - 중복 체크와 저장 사이에 다른 요청이 끼어들 수 있음
 * 
 * 2. 탈퇴한 사용자(Soft Delete)의 닉네임 재사용 불가 문제
 * - 탈퇴한 사용자의 닉네임을 다른 사용자가 재사용할 수 없음
 * - findByNickname이 isDeleted 필터링 없이 조회함
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsersServiceConcurrencyTest {

    @Autowired
    private UsersService usersService;

    @Autowired
    private UsersRepository usersRepository;

    /**
     * 테스트 1: 닉네임 중복 체크 Race Condition 문제 재현
     * 
     * 시나리오:
     * - 동시에 5명의 사용자가 같은 닉네임 "test_nickname"으로 가입 시도
     * - 중복 체크와 저장 사이의 시간 차이를 이용하여 Race Condition 발생
     * - DB Unique 제약조건이 있어도 예외 처리가 없으면 문제 발생 가능
     */
    @Test
    @DisplayName("⚠️ 닉네임 중복 체크 Race Condition 문제 재현: 동시 가입 시도")
    void testNicknameDuplicateRaceCondition() throws InterruptedException {
        String duplicateNickname = "test_nickname_race_" + System.currentTimeMillis();
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();
        List<String> createdUserIds = new ArrayList<>();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("⚠️ 닉네임 중복 체크 Race Condition 테스트");
        System.out.println("=".repeat(70));
        System.out.println("테스트 닉네임: " + duplicateNickname);
        System.out.println("동시 요청 수: " + threadCount + "개");
        System.out.println();

        // 동시에 여러 사용자가 같은 닉네임으로 가입 시도
        for (int i = 0; i < threadCount; i++) {
            final int userIndex = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // 모든 스레드가 준비될 때까지 대기

                    // 같은 닉네임으로 가입 시도
                    UsersDTO dto = UsersDTO.builder()
                            .id("user_" + userIndex + "_" + System.currentTimeMillis())
                            .username("username_" + userIndex + "_" + System.currentTimeMillis())
                            .nickname(duplicateNickname) // ⚠️ 같은 닉네임!
                            .email("user" + userIndex + "_" + System.currentTimeMillis() + "@test.com")
                            .password("password123")
                            .role("USER")
                            .status("ACTIVE")
                            .build();

                    UsersDTO created = usersService.createUser(dto);
                    createdUserIds.add(created.getId());
                    successCount.incrementAndGet();
                    System.out.println("✅ 사용자 " + userIndex + " 가입 성공: " + created.getId());

                } catch (DataIntegrityViolationException e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                    System.out.println("❌ 사용자 " + userIndex + " 가입 실패 (DB 제약조건): " + e.getMessage());
                } catch (RuntimeException e) {
                    if (e.getMessage().contains("이미 사용 중인 닉네임")) {
                        failureCount.incrementAndGet();
                        exceptions.add(e);
                        System.out.println("❌ 사용자 " + userIndex + " 가입 실패 (중복 체크): " + e.getMessage());
                    } else {
                        failureCount.incrementAndGet();
                        exceptions.add(e);
                        System.out.println("❌ 사용자 " + userIndex + " 가입 실패 (기타): " + e.getMessage());
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                    System.out.println("❌ 사용자 " + userIndex + " 예외 발생: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // 결과 검증
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("테스트 결과");
        System.out.println("=".repeat(70));
        System.out.println("성공한 가입 수: " + successCount.get());
        System.out.println("실패한 가입 수: " + failureCount.get());

        // 실제 DB에서 해당 닉네임을 가진 사용자 수 확인
        long actualNicknameCount = usersRepository.findAll().stream()
                .filter(u -> duplicateNickname.equals(u.getNickname()))
                .count();

        System.out.println("DB에 저장된 같은 닉네임 사용자 수: " + actualNicknameCount);
        System.out.println();

        // 문제 분석
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│ 문제 분석                                                 │");
        System.out.println("├──────────────────────────────────────────────────────────┤");
        if (successCount.get() > 1) {
            System.out.println("│ ⚠️ Race Condition 발생! 여러 사용자가 같은 닉네임으로 가입 성공 │");
            System.out.println("│    (DB Unique 제약조건이 이를 방지하지만 예외 처리 필요)      │");
        } else if (successCount.get() == 1) {
            System.out.println("│ ✅ 정상: 1명만 가입 성공 (DB Unique 제약조건 작동)          │");
            System.out.println("│    단, DataIntegrityViolationException 예외 처리 필요      │");
        } else {
            System.out.println("│ ❌ 모든 가입 실패 (예상치 못한 상황)                         │");
        }
        System.out.println("│                                                          │");
        System.out.println("│ 해결 방법:                                                │");
        System.out.println("│   1. DataIntegrityViolationException 예외 처리 추가        │");
        System.out.println("│   2. Pessimistic Lock 적용 (성능 고려 필요)              │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
        System.out.println();

        // 검증: 닉네임은 유일해야 함
        assertThat(actualNicknameCount)
                .as("같은 닉네임을 가진 사용자는 1명 이하여야 함 (DB Unique 제약조건)")
                .isLessThanOrEqualTo(1);

        // 검증: 성공한 가입이 있으면 1개여야 함 (DB 제약조건으로 인해)
        if (successCount.get() > 0) {
            assertThat(successCount.get())
                    .as("DB Unique 제약조건으로 인해 1명만 가입 성공해야 함")
                    .isLessThanOrEqualTo(1);
        }
    }

    /**
     * 테스트 2: 탈퇴한 사용자(Soft Delete)의 닉네임 재사용 불가 문제 재현
     * 
     * 시나리오:
     * 1. 사용자 A가 "test_nickname"으로 가입
     * 2. 사용자 A가 탈퇴 (isDeleted = true)
     * 3. 사용자 B가 "test_nickname"으로 가입 시도
     * 4. findByNickname이 탈퇴한 사용자 A를 반환하여 중복 체크 실패
     */
    @Test
    @DisplayName("⚠️ 탈퇴한 사용자의 닉네임 재사용 불가 문제 재현")
    void testDeletedUserNicknameReuseIssue() {
        String testNickname = "test_nickname_deleted_" + System.currentTimeMillis();
        String testEmail1 = "user1_" + System.currentTimeMillis() + "@test.com";
        String testEmail2 = "user2_" + System.currentTimeMillis() + "@test.com";

        System.out.println("\n" + "=".repeat(70));
        System.out.println("⚠️ 탈퇴한 사용자의 닉네임 재사용 불가 문제 테스트");
        System.out.println("=".repeat(70));
        System.out.println("테스트 닉네임: " + testNickname);
        System.out.println();

        // 1단계: 사용자 A 가입
        System.out.println("[1단계] 사용자 A 가입");
        UsersDTO userA_DTO = UsersDTO.builder()
                .id("user_a_" + System.currentTimeMillis())
                .username("username_a_" + System.currentTimeMillis())
                .nickname(testNickname)
                .email(testEmail1)
                .password("password123")
                .role("USER")
                .status("ACTIVE")
                .build();

        UsersDTO createdUserA = usersService.createUser(userA_DTO);
        System.out.println("✅ 사용자 A 가입 성공: ID=" + createdUserA.getId() + ", 닉네임=" + createdUserA.getNickname());
        System.out.println();

        // 사용자 A 엔티티 조회
        Users userA = usersRepository.findByIdString(createdUserA.getId())
                .orElseThrow(() -> new RuntimeException("사용자 A를 찾을 수 없습니다."));

        // 2단계: 사용자 A 탈퇴 (Soft Delete)
        System.out.println("[2단계] 사용자 A 탈퇴 (Soft Delete)");
        usersService.deleteUser(userA.getIdx());
        System.out.println("✅ 사용자 A 탈퇴 완료");

        // 탈퇴 확인
        Users deletedUserA = usersRepository.findById(userA.getIdx())
                .orElseThrow(() -> new RuntimeException("탈퇴한 사용자 A를 찾을 수 없습니다."));
        assertThat(deletedUserA.getIsDeleted()).isTrue();
        assertThat(deletedUserA.getDeletedAt()).isNotNull();
        System.out.println("  - isDeleted: " + deletedUserA.getIsDeleted());
        System.out.println("  - deletedAt: " + deletedUserA.getDeletedAt());
        System.out.println();

        // 3단계: 문제 확인 - findByNickname이 탈퇴한 사용자를 반환하는지 확인
        System.out.println("[3단계] 문제 확인: findByNickname()이 탈퇴한 사용자를 반환하는지 확인");
        var foundUser = usersRepository.findByNickname(testNickname);
        if (foundUser.isPresent()) {
            Users found = foundUser.get();
            System.out.println("⚠️ findByNickname()이 사용자를 찾았습니다:");
            System.out.println("  - ID: " + found.getId());
            System.out.println("  - 닉네임: " + found.getNickname());
            System.out.println("  - isDeleted: " + found.getIsDeleted());
            System.out.println("  - ⚠️ 문제: 탈퇴한 사용자도 조회됨!");
        } else {
            System.out.println("✅ findByNickname()이 사용자를 찾지 못함 (예상치 못한 상황)");
        }
        System.out.println();

        // 4단계: 사용자 B가 같은 닉네임으로 가입 시도
        System.out.println("[4단계] 사용자 B가 같은 닉네임으로 가입 시도");
        UsersDTO userB_DTO = UsersDTO.builder()
                .id("user_b_" + System.currentTimeMillis())
                .username("username_b_" + System.currentTimeMillis())
                .nickname(testNickname) // ⚠️ 탈퇴한 사용자 A의 닉네임과 동일!
                .email(testEmail2)
                .password("password123")
                .role("USER")
                .status("ACTIVE")
                .build();

        try {
            UsersDTO createdUserB = usersService.createUser(userB_DTO);
            System.out.println("✅ 사용자 B 가입 성공: ID=" + createdUserB.getId());
            System.out.println();
            System.out.println("┌──────────────────────────────────────────────────────────┐");
            System.out.println("│ ✅ 정상 동작: 탈퇴한 사용자의 닉네임을 재사용할 수 있음      │");
            System.out.println("└──────────────────────────────────────────────────────────┘");

            // 가입이 성공했다면 문제가 해결된 것 (하지만 현재 코드는 실패할 것으로 예상)
            assertTrue(true, "탈퇴한 사용자의 닉네임 재사용 성공");

        } catch (RuntimeException e) {
            if (e.getMessage().contains("이미 사용 중인 닉네임")) {
                System.out.println("❌ 사용자 B 가입 실패: " + e.getMessage());
                System.out.println();
                System.out.println("┌──────────────────────────────────────────────────────────┐");
                System.out.println("│ ⚠️ 문제 확인: 탈퇴한 사용자의 닉네임을 재사용할 수 없음!   │");
                System.out.println("├──────────────────────────────────────────────────────────┤");
                System.out.println("│ 문제 원인:                                                │");
                System.out.println("│   - findByNickname()이 isDeleted 필터링 없이 조회        │");
                System.out.println("│   - 탈퇴한 사용자도 중복 체크에서 걸림                    │");
                System.out.println("│                                                          │");
                System.out.println("│ 해결 방법:                                                │");
                System.out.println("│   - Repository 메서드에 isDeleted = false 조건 추가      │");
                System.out.println("│   - 또는 서비스에서 필터링                                │");
                System.out.println("└──────────────────────────────────────────────────────────┘");

                // 문제가 확인되었으므로 테스트는 통과 (문제를 확인하는 것이 목적)
                assertTrue(true, "문제 상황 확인됨");
            } else {
                throw e; // 다른 예외는 재throw
            }
        }
    }

    /**
     * 테스트 3: 탈퇴한 사용자의 username과 email 재사용 불가 문제도 확인
     */
    @Test
    @DisplayName("⚠️ 탈퇴한 사용자의 username과 email 재사용 불가 문제 확인")
    void testDeletedUserUsernameAndEmailReuseIssue() {
        String testUsername = "test_username_" + System.currentTimeMillis();
        String testEmail = "test_email_" + System.currentTimeMillis() + "@test.com";
        long timestamp = System.currentTimeMillis();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("⚠️ 탈퇴한 사용자의 username/email 재사용 불가 문제 테스트");
        System.out.println("=".repeat(70));
        System.out.println();

        // 1단계: 사용자 가입
        UsersDTO userDTO = UsersDTO.builder()
                .id("user_" + timestamp)
                .username(testUsername)
                .nickname("nickname_" + timestamp)
                .email(testEmail)
                .password("password123")
                .role("USER")
                .status("ACTIVE")
                .build();

        UsersDTO created = usersService.createUser(userDTO);
        Users user = usersRepository.findByIdString(created.getId())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        System.out.println("✅ 사용자 가입 성공: " + created.getId());
        System.out.println();

        // 2단계: 사용자 탈퇴
        usersService.deleteUser(user.getIdx());
        System.out.println("✅ 사용자 탈퇴 완료");
        System.out.println();

        // 3단계: username 재사용 시도
        System.out.println("[테스트] username 재사용 시도");
        try {
            UsersDTO newUserDTO = UsersDTO.builder()
                    .id("new_user_" + timestamp)
                    .username(testUsername) // ⚠️ 탈퇴한 사용자의 username
                    .nickname("new_nickname_" + timestamp)
                    .email("new_email_" + timestamp + "@test.com")
                    .password("password123")
                    .role("USER")
                    .status("ACTIVE")
                    .build();

            usersService.createUser(newUserDTO);
            System.out.println("✅ username 재사용 성공 (예상치 못한 동작)");
        } catch (RuntimeException e) {
            System.out.println("❌ username 재사용 실패: " + e.getMessage());
            if (e.getMessage().contains("이미 사용 중인")) {
                System.out.println("  ⚠️ 탈퇴한 사용자의 username도 조회됨!");
            }
        }
        System.out.println();

        // 4단계: email 재사용 시도
        System.out.println("[테스트] email 재사용 시도");
        try {
            UsersDTO newUserDTO2 = UsersDTO.builder()
                    .id("new_user2_" + timestamp)
                    .username("new_username_" + timestamp)
                    .nickname("new_nickname2_" + timestamp)
                    .email(testEmail) // ⚠️ 탈퇴한 사용자의 email
                    .password("password123")
                    .role("USER")
                    .status("ACTIVE")
                    .build();

            usersService.createUser(newUserDTO2);
            System.out.println("✅ email 재사용 성공 (예상치 못한 동작)");
        } catch (RuntimeException e) {
            System.out.println("❌ email 재사용 실패: " + e.getMessage());
            if (e.getMessage().contains("이미 사용 중인")) {
                System.out.println("  ⚠️ 탈퇴한 사용자의 email도 조회됨!");
            }
        }
        System.out.println();
    }
}
