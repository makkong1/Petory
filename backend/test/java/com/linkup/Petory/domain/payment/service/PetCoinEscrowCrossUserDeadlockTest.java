package com.linkup.Petory.domain.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.repository.PetCoinTransactionRepository;
import com.linkup.Petory.domain.payment.repository.SpringDataJpaPetCoinEscrowRepository;
import com.linkup.Petory.domain.payment.repository.SpringDataJpaPetCoinTransactionRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 에스크로 크로스-유저 락 데드락 프로브.
 *
 * <p>createEscrow 는 {@code deductCoins(requester)} 로 요청자 행에 X락(SELECT ... FOR UPDATE)을 잡은 뒤,
 * escrow INSERT 로 provider 행에 FK 공유(S)락을 새로 잡는다. 서로 다른 두 거래에서 유저 역할이 뒤바뀌면
 * (Deal1: requester=A/provider=B, Deal2: requester=B/provider=A) 다음 순환 대기가 성립한다:
 * <pre>
 *   Deal1: X(A) 보유 → S(B) 대기
 *   Deal2: X(B) 보유 → S(A) 대기
 * </pre>
 * 실제 운영에서 두 거래는 서로 다른 conversation 이라 confirmCareDeal 의 conversation 락으로 직렬화되지 않는다.
 * 이 테스트는 createEscrow 를 직접 호출해 그 락 구간만 격리하여, 크로스-유저 데드락이 실재하는지 관찰한다.
 *
 * <p>동시성 테스트이므로 @Transactional 롤백을 쓰지 않고 tearDown 에서 명시적으로 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PetCoinEscrowCrossUserDeadlockTest {

    private static final Logger log = LoggerFactory.getLogger(PetCoinEscrowCrossUserDeadlockTest.class);

    @Autowired
    private PetCoinEscrowService petCoinEscrowService;
    @Autowired
    private UsersRepository usersRepository;
    @Autowired
    private CareRequestRepository careRequestRepository;
    @Autowired
    private SpringDataJpaPetCoinEscrowRepository escrowJpa;
    @Autowired
    private PetCoinTransactionRepository transactionRepository;
    @Autowired
    private SpringDataJpaPetCoinTransactionRepository txJpa;

    private static final int ROUNDS = 30;
    private static final int AMOUNT = 10;

    private final long uid = System.currentTimeMillis();
    private Users userA;
    private Users userB;
    private final List<CareRequest> createdRequests = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        userA = usersRepository.save(Users.builder()
                .id("escrow_A_" + uid)
                .username("escrow_A_" + uid)
                .email("escrow_A_" + uid + "@test.petory.local")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .petCoinBalance(ROUNDS * AMOUNT + 100)
                .build());
        userB = usersRepository.save(Users.builder()
                .id("escrow_B_" + uid)
                .username("escrow_B_" + uid)
                .email("escrow_B_" + uid + "@test.petory.local")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .petCoinBalance(ROUNDS * AMOUNT + 100)
                .build());
    }

    @AfterEach
    void tearDown() {
        for (CareRequest cr : createdRequests) {
            try {
                escrowJpa.findByCareRequest(cr).ifPresent(e -> escrowJpa.deleteById(e.getIdx()));
            } catch (Exception e) {
                log.warn("[tearDown] escrow 삭제 실패 (무시): {}", e.getMessage());
            }
        }
        for (Users u : List.of(userA, userB)) {
            try {
                List<PetCoinTransaction> txs = transactionRepository
                        .findByUserOrderByCreatedAtDesc(u, Pageable.unpaged()).getContent();
                if (!txs.isEmpty()) {
                    txJpa.deleteAll(txs);
                }
            } catch (Exception e) {
                log.warn("[tearDown] 거래내역 삭제 실패 (무시): {}", e.getMessage());
            }
        }
        for (CareRequest cr : createdRequests) {
            try {
                careRequestRepository.deleteById(cr.getIdx());
            } catch (Exception e) {
                log.warn("[tearDown] careRequest 삭제 실패 (무시): {}", e.getMessage());
            }
        }
        try {
            usersRepository.deleteById(userA.getIdx());
            usersRepository.deleteById(userB.getIdx());
        } catch (Exception e) {
            log.warn("[tearDown] 사용자 삭제 실패 (무시): {}", e.getMessage());
        }
    }

    private CareRequest newRequest(Users owner, int roundIndex) {
        CareRequest cr = CareRequest.builder()
                .user(owner)
                .title("escrow-deadlock-probe-" + uid + "-" + roundIndex)
                .description("cross-user escrow deadlock probe")
                .date(LocalDateTime.now().plusDays(1))
                .status(CareRequestStatus.OPEN)
                .build();
        cr = careRequestRepository.save(cr);
        createdRequests.add(cr);
        return cr;
    }

    @Test
    @DisplayName("에스크로 크로스-유저 락: 역할이 뒤바뀐 두 거래를 동시에 createEscrow 하면 데드락이 나는가?")
    void crossUserSwappedRole_escrowCreation_deadlockProbe() throws InterruptedException {
        // 각 라운드마다 서로 반대 방향의 거래 2건을 미리 만들어 둔다 (모두 커밋된 상태에서 레이스).
        List<CareRequest> aToB = new ArrayList<>(); // requester=A, provider=B
        List<CareRequest> bToA = new ArrayList<>(); // requester=B, provider=A
        for (int i = 0; i < ROUNDS; i++) {
            aToB.add(newRequest(userA, i));
            bToA.add(newRequest(userB, i));
        }

        int total = ROUNDS * 2;
        ExecutorService executor = Executors.newFixedThreadPool(total);
        CountDownLatch ready = new CountDownLatch(total);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger otherFailCount = new AtomicInteger(0);
        List<String> deadlockMsgs = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < ROUNDS; i++) {
            final CareRequest crAB = aToB.get(i);
            final CareRequest crBA = bToA.get(i);

            executor.submit(() -> runOne(crAB, userA, userB, ready,
                    successCount, deadlockCount, otherFailCount, deadlockMsgs));
            executor.submit(() -> runOne(crBA, userB, userA, ready,
                    successCount, deadlockCount, otherFailCount, deadlockMsgs));
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(60, TimeUnit.SECONDS);

        log.info("\n========== [에스크로 크로스-유저 데드락 프로브] 결과 ==========");
        log.info("전체 요청: {}, 성공: {}, 데드락: {}, 기타실패: {}, 종료완료: {}",
                total, successCount.get(), deadlockCount.get(), otherFailCount.get(), terminated);
        if (!deadlockMsgs.isEmpty()) {
            log.info("---- 데드락/락 예외 샘플 ----");
            deadlockMsgs.stream().limit(5).forEach(m -> log.info("  - {}", m));
        }
        log.info("=============================================================\n");

        // 크로스-유저 데드락이 실재하면 여기서 성공 수가 전체보다 작아져 빨간불이 된다.
        assertEquals(total, successCount.get(),
                "역할이 뒤바뀐 동시 createEscrow 가 데드락 없이 모두 성공해야 함 "
                        + "(데드락 " + deadlockCount.get() + "건, 기타실패 " + otherFailCount.get() + "건)");
    }

    private void runOne(CareRequest careRequest, Users requester, Users provider, CountDownLatch ready,
            AtomicInteger successCount, AtomicInteger deadlockCount, AtomicInteger otherFailCount,
            List<String> deadlockMsgs) {
        try {
            ready.countDown();
            ready.await();
            petCoinEscrowService.createEscrow(careRequest, null, requester, provider, AMOUNT);
            successCount.incrementAndGet();
        } catch (Exception e) {
            if (isDeadlock(e)) {
                deadlockCount.incrementAndGet();
                deadlockMsgs.add(e.getClass().getSimpleName() + ": " + rootMessage(e));
            } else {
                otherFailCount.incrementAndGet();
                deadlockMsgs.add("[other] " + e.getClass().getSimpleName() + ": " + rootMessage(e));
            }
        }
    }

    private boolean isDeadlock(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Deadlock") || msg.contains("Lock wait timeout"))) {
                return true;
            }
            String cn = t.getClass().getName();
            if (cn.contains("DeadlockLoserDataAccessException")
                    || cn.contains("CannotAcquireLockException")) {
                return true;
            }
        }
        return false;
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
