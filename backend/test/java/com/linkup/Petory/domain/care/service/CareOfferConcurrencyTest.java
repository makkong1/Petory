package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 제안은 여러 명에게 동시에 나갈 수 있다. 그래서 두 제공자가 같은 순간에 수락을 누를 수 있다.
 *
 * <p>수락은 "아직 모집 중인가"를 읽고 판단해서 상태 전이·지급 대상 배정을 쓰는 구간이다.
 * 둘 다 모집 중을 읽고 각자 넘어가면 <strong>지급 대상이 덮어써진다</strong> — 한 명은 자기가
 * 맡은 줄 알고 일하는데 돈은 다른 사람에게 배정돼 있게 된다. 읽고 판단해서 쓰는 구간이라
 * 원자적 UPDATE 로는 대체되지 않아 요청 행을 잠가 직렬화한다.
 *
 * <p>대칭 확정 시절에는 같은 자리를 <em>채팅방</em> 행으로 잠갔다. 방이 제공자마다 따로 있으니
 * 서로 다른 행을 잠그게 되어 경쟁하는 두 수락을 직렬화하지 못한다 — 잠그는 대상이 경쟁하는
 * 자원과 달랐다. 요청 단위로 옮기면서 그 어긋남도 같이 없어졌다.
 */
@SpringBootTest
class CareOfferConcurrencyTest {

    private static final int INITIAL_BALANCE = 50_000;
    private static final int OFFERED = 5_000;

    @Autowired
    private CareRequestService careRequestService;

    @Autowired
    private CareOfferService careOfferService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private CareApplicationRepository careApplicationRepository;

    @Autowired
    private PetCoinEscrowRepository escrowRepository;

    private Users requester;
    private final List<Users> providers = new ArrayList<>();
    private final List<Long> offerIds = new ArrayList<>();
    private Long careRequestIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.nanoTime();

        requester = usersRepository.save(Users.builder()
                .id("conc_req_" + uniqueId).username("ConcReq_" + uniqueId)
                .email("conc_req_" + uniqueId + "@test.com").password("password123")
                .nickname("ConcReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Offer Concurrency Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .offeredCoins(OFFERED)
                .latitude(37.5).longitude(127.0).address("서울시 어딘가")
                .build()).getIdx();

        for (int i = 0; i < 2; i++) {
            Users provider = usersRepository.save(Users.builder()
                    .id("conc_prv" + i + "_" + uniqueId).username("ConcPrv" + i + "_" + uniqueId)
                    .email("conc_prv" + i + "_" + uniqueId + "@test.com").password("password123")
                    .nickname("ConcPrv" + i + "_" + uniqueId).role(Role.SERVICE_PROVIDER)
                    .emailVerified(true).build());
            providers.add(provider);
            offerIds.add(careOfferService
                    .offerTo(careRequestIdx, provider.getIdx(), requester.getIdx())
                    .getIdx());
        }
    }

    @AfterEach
    void tearDown() {
        if (careRequestIdx != null) {
            careRequestRepository.deleteById(careRequestIdx);   // 에스크로는 FK CASCADE
        }
        for (Users p : providers) {
            usersRepository.deleteById(p.getIdx());
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
        providers.clear();
        offerIds.clear();
    }

    @Test
    @DisplayName("두 제공자가 동시에 수락해도 계약은 한 건만 성립하고 지급 대상도 한 명이다")
    void 동시_수락은_하나만_성립() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    careOfferService.acceptOffer(offerIds.get(index), providers.get(index).getIdx());
                    succeeded.incrementAndGet();
                } catch (Exception expectedForLoser) {
                    // 진 쪽은 "이미 확정된 요청"으로 거절된다 — 정상 경로다.
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        done.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(succeeded.get())
                .as("둘 다 성공하면 한 명은 돈 없이 일하게 된다")
                .isEqualTo(1);

        var request = careRequestRepository.findById(careRequestIdx).orElseThrow();
        assertThat(request.getStatus())
                .as("경쟁 때문에 양쪽 다 스킵되어 모집 중으로 남아도 안 된다")
                .isEqualTo(CareRequestStatus.IN_PROGRESS);

        long accepted = offerIds.stream()
                .map(id -> careApplicationRepository.findById(id).orElseThrow().getStatus())
                .filter(s -> s == CareApplicationStatus.ACCEPTED)
                .count();
        assertThat(accepted).isEqualTo(1);

        var escrow = escrowRepository.findByCareRequest(request).orElseThrow();
        assertThat(escrow.getProvider()).isNotNull();
    }
}
