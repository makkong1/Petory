package com.linkup.Petory.domain.payment.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.repository.PetCoinTransactionRepository;
import com.linkup.Petory.domain.payment.repository.SpringDataJpaPetCoinTransactionRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PetCoinService Race Condition 테스트
 *
 * <p>문제 시나리오:
 * <ul>
 *   <li>chargeCoins, payoutCoins, refundCoins는 findById 사용 (락 없음)</li>
 *   <li>동시 요청 시 둘 다 동일한 balanceBefore 조회 → balanceAfter 덮어쓰기 → Lost Update</li>
 *   <li>예: 초기 100, 동시 충전 50+30 → 예상 180, 실제 130 또는 150 (1건 분실)</li>
 * </ul>
 *
 * <p>해결: findByIdForUpdate 적용 후 "해결 후" 테스트가 통과해야 함.
 *
 * <p>DB 영향: 테스트 전용 User 생성, @AfterEach에서 거래내역·사용자 삭제로 실제 데이터 보호.
 *
 * @see docs/refactoring/payment/petcoin-service-race-condition.md
 */
@SpringBootTest
@ActiveProfiles("test")
class PetCoinServiceRaceConditionTest {

    @Autowired
    private PetCoinService petCoinService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PetCoinTransactionRepository transactionRepository;

    @Autowired
    private SpringDataJpaPetCoinTransactionRepository jpaTransactionRepository;

    private static final Logger log = LoggerFactory.getLogger(PetCoinServiceRaceConditionTest.class);

    private Users testUser;
    private final long uniqueId = System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        log.info("========================================");
        log.info("[PetCoinServiceRaceConditionTest] setUp 시작 - uniqueId={}", uniqueId);
        log.info("========================================");

        testUser = Users.builder()
                .id("race_test_user_" + uniqueId)
                .username("race_test_" + uniqueId)
                .email("race_test_" + uniqueId + "@test.petory.local")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .petCoinBalance(100)
                .build();
        testUser = usersRepository.save(testUser);

        log.info("[setUp] 테스트 사용자 생성 완료: idx={}, id={}, 초기잔액={}",
                testUser.getIdx(), testUser.getId(), testUser.getPetCoinBalance());
        log.info("========================================\n");
    }

    @AfterEach
    void tearDown() {
        log.info("\n========================================");
        log.info("[PetCoinServiceRaceConditionTest] tearDown 시작 - testUser.idx={}", testUser != null ? testUser.getIdx() : null);
        log.info("========================================");

        if (testUser != null) {
            try {
                List<PetCoinTransaction> transactions = transactionRepository.findByUserOrderByCreatedAtDesc(testUser);
                if (!transactions.isEmpty()) {
                    jpaTransactionRepository.deleteAll(transactions);
                    log.info("[tearDown] 거래 내역 {}건 삭제 완료 (user_idx={})", transactions.size(), testUser.getIdx());
                }
                usersRepository.deleteById(testUser.getIdx());
                log.info("[tearDown] 테스트 사용자 삭제 완료: idx={}", testUser.getIdx());
            } catch (Exception e) {
                log.warn("[tearDown] 정리 중 예외 (무시): {}", e.getMessage());
            }
        }
        log.info("========================================\n");
    }

    @Test
    @DisplayName("❌ 문제 상황: chargeCoins 동시 충전 시 Lost Update 재현 (findById 사용)")
    void testChargeCoins_RaceCondition_ProblemOccurs() throws InterruptedException {
        int concurrentCount = 5;
        int chargeAmount = 10;
        int expectedTotalCharge = concurrentCount * chargeAmount; // 50
        int initialBalance = testUser.getPetCoinBalance(); // 100
        int expectedFinalBalance = initialBalance + expectedTotalCharge; // 150

        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch startLatch = new CountDownLatch(concurrentCount);
        CountDownLatch readyLatch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        log.info("\n========== [문제 상황] chargeCoins Race Condition 테스트 ==========");
        log.info("초기 잔액: {}, 동시 충전: {}건 x {} = {} (예상 최종: {})",
                initialBalance, concurrentCount, chargeAmount, expectedTotalCharge, expectedFinalBalance);
        log.info("현재 chargeCoins는 findById 사용 → 락 없음 → Lost Update 가능");
        log.info("================================================================\n");

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    readyLatch.await();

                    long startMs = System.currentTimeMillis();
                    String threadName = Thread.currentThread().getName();

                    log.info("[충전-{}] {} 시작 | user_idx={}, amount={}",
                            index, threadName, testUser.getIdx(), chargeAmount);
                    logs.add(String.format("[%s] 충전-%d 시작 amount=%d", LocalDateTime.now(), index, chargeAmount));

                    PetCoinTransaction tx = petCoinService.chargeCoins(
                            testUser, chargeAmount, "RaceCondition테스트-" + index);

                    long durationMs = System.currentTimeMillis() - startMs;
                    Integer balanceAfter = tx.getBalanceAfter();

                    log.info("[충전-{}] {} 완료 | 소요={}ms | balanceBefore={}, balanceAfter={}",
                            index, threadName, durationMs, tx.getBalanceBefore(), balanceAfter);
                    logs.add(String.format("[%s] 충전-%d 완료 balanceAfter=%d (소요=%dms)",
                            LocalDateTime.now(), index, balanceAfter, durationMs));

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[충전-{}] 실패: {}", index, e.getMessage());
                    logs.add(String.format("[%s] 충전-%d 실패: %s", LocalDateTime.now(), index, e.getMessage()));
                    exceptions.add(e);
                    failureCount.incrementAndGet();
                }
            });
            readyLatch.countDown();
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(15, TimeUnit.SECONDS);
        if (!terminated) {
            log.warn("일부 스레드 15초 내 미완료");
            executor.shutdownNow();
        }

        Users refreshedUser = usersRepository.findById(testUser.getIdx()).orElseThrow();
        Integer finalBalance = petCoinService.getBalance(refreshedUser);
        List<PetCoinTransaction> allTx = transactionRepository.findByUserOrderByCreatedAtDesc(testUser);

        log.info("\n========== [문제 상황] 테스트 결과 ==========");
        log.info("성공: {}건, 실패: {}건", successCount.get(), failureCount.get());
        log.info("예상 최종 잔액: {}", expectedFinalBalance);
        log.info("실제 최종 잔액: {}", finalBalance);
        log.info("거래 내역 수: {}", allTx.size());
        log.info("==========================================\n");

        log.info("========== 상세 로그 (시간순) ==========");
        logs.forEach(log::info);
        log.info("=====================================\n");

        boolean raceConditionOccurred = !finalBalance.equals(expectedFinalBalance);
        if (raceConditionOccurred) {
            log.warn("⚠️ Race Condition 발생! 예상={}, 실제={} → Lost Update 확인", expectedFinalBalance, finalBalance);
            log.warn("   chargeCoins에 findByIdForUpdate 적용 필요");
        } else {
            log.info("이번 실행에서는 Race Condition 미발생 (타이밍에 따라 발생 여부 달라짐)");
        }

        if (!exceptions.isEmpty()) {
            log.info("========== 발생한 예외 ==========");
            exceptions.forEach(e -> log.info("  - {}: {}", e.getClass().getSimpleName(), e.getMessage()));
            log.info("===============================\n");
        }

        assertEquals(concurrentCount, successCount.get(),
                "모든 충전 요청이 성공해야 함 (예외 없이)");
    }

    @Test
    @DisplayName("✅ 해결 후: chargeCoins findByIdForUpdate 적용 시 잔액 일관성 검증")
    void testChargeCoins_RaceCondition_Fixed() throws InterruptedException {
        int concurrentCount = 5;
        int chargeAmount = 10;
        int expectedTotalCharge = concurrentCount * chargeAmount;
        int initialBalance = testUser.getPetCoinBalance();
        int expectedFinalBalance = initialBalance + expectedTotalCharge;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch readyLatch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        log.info("\n========== [해결 후] chargeCoins 정상 동작 검증 ==========");
        log.info("chargeCoins에 findByIdForUpdate 적용 시 이 테스트가 통과해야 함");
        log.info("초기: {}, 동시 충전: {} x {} = {} → 예상 최종: {}",
                initialBalance, concurrentCount, chargeAmount, expectedTotalCharge, expectedFinalBalance);
        log.info("================================================================\n");

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    long startMs = System.currentTimeMillis();

                    petCoinService.chargeCoins(testUser, chargeAmount, "해결후테스트-" + index);

                    long durationMs = System.currentTimeMillis() - startMs;
                    logs.add(String.format("[%s] 충전-%d 완료 (소요=%dms)", LocalDateTime.now(), index, durationMs));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[충전-{}] 실패: {}", index, e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            readyLatch.countDown();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "15초 내 완료");

        Users refreshedUser = usersRepository.findById(testUser.getIdx()).orElseThrow();
        Integer finalBalance = petCoinService.getBalance(refreshedUser);

        log.info("\n========== [해결 후] 결과 ==========");
        log.info("성공: {}건, 최종 잔액: {} (예상: {})", successCount.get(), finalBalance, expectedFinalBalance);
        logs.forEach(log::info);
        log.info("====================================\n");

        assertEquals(concurrentCount, successCount.get(), "모든 충전 성공");
        assertEquals(expectedFinalBalance, finalBalance,
                "findByIdForUpdate 적용 시 최종 잔액이 예상과 일치해야 함 (Lost Update 없음)");
    }

    @Test
    @DisplayName("❌ 문제 상황: payoutCoins 동시 지급 시 Lost Update 재현")
    void testPayoutCoins_RaceCondition_ProblemOccurs() throws InterruptedException {
        int concurrentCount = 5;
        int payoutAmount = 10;
        int expectedTotal = concurrentCount * payoutAmount;
        int initialBalance = testUser.getPetCoinBalance();
        int expectedFinalBalance = initialBalance + expectedTotal;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch readyLatch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        log.info("\n========== [문제 상황] payoutCoins Race Condition ==========");
        log.info("초기: {}, 동시 지급: {} x {} → 예상 최종: {}", initialBalance, concurrentCount, payoutAmount, expectedFinalBalance);
        log.info("================================================================\n");

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    long startMs = System.currentTimeMillis();

                    petCoinService.payoutCoins(testUser, payoutAmount,
                            "TEST", (long) index, "RaceCondition테스트-" + index);

                    logs.add(String.format("[%s] 지급-%d 완료 (소요=%dms)", LocalDateTime.now(), index,
                            System.currentTimeMillis() - startMs));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[지급-{}] 실패: {}", index, e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            readyLatch.countDown();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        Users refreshedUser = usersRepository.findById(testUser.getIdx()).orElseThrow();
        Integer finalBalance = petCoinService.getBalance(refreshedUser);

        log.info("\n========== [payoutCoins] 결과 ==========");
        log.info("성공: {}건, 최종: {} (예상: {})", successCount.get(), finalBalance, expectedFinalBalance);
        logs.forEach(log::info);
        log.info("======================================\n");

        assertEquals(concurrentCount, successCount.get());
        if (!finalBalance.equals(expectedFinalBalance)) {
            log.warn("⚠️ payoutCoins Race Condition 발생. findByIdForUpdate 적용 필요.");
        }
    }

    @Test
    @DisplayName("❌ 문제 상황: refundCoins 동시 환불 시 Lost Update 재현")
    void testRefundCoins_RaceCondition_ProblemOccurs() throws InterruptedException {
        int concurrentCount = 5;
        int refundAmount = 10;
        int expectedTotal = concurrentCount * refundAmount;
        int initialBalance = testUser.getPetCoinBalance();
        int expectedFinalBalance = initialBalance + expectedTotal;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch readyLatch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> logs = Collections.synchronizedList(new ArrayList<>());

        log.info("\n========== [문제 상황] refundCoins Race Condition ==========");
        log.info("초기: {}, 동시 환불: {} x {} → 예상 최종: {}", initialBalance, concurrentCount, refundAmount, expectedFinalBalance);
        log.info("================================================================\n");

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    readyLatch.await();
                    long startMs = System.currentTimeMillis();

                    petCoinService.refundCoins(testUser, refundAmount,
                            "TEST", (long) index, "RaceCondition테스트-" + index);

                    logs.add(String.format("[%s] 환불-%d 완료 (소요=%dms)", LocalDateTime.now(), index,
                            System.currentTimeMillis() - startMs));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[환불-{}] 실패: {}", index, e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            readyLatch.countDown();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        Users refreshedUser = usersRepository.findById(testUser.getIdx()).orElseThrow();
        Integer finalBalance = petCoinService.getBalance(refreshedUser);

        log.info("\n========== [refundCoins] 결과 ==========");
        log.info("성공: {}건, 최종: {} (예상: {})", successCount.get(), finalBalance, expectedFinalBalance);
        logs.forEach(log::info);
        log.info("======================================\n");

        assertEquals(concurrentCount, successCount.get());
        if (!finalBalance.equals(expectedFinalBalance)) {
            log.warn("⚠️ refundCoins Race Condition 발생. findByIdForUpdate 적용 필요.");
        }
    }
}
