package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 스케줄러가 돈을 자동으로 옮기지 않는다는 것을 고정한다.
 *
 * 예전에는 예정일이 지난 OPEN/IN_PROGRESS 를 모두 COMPLETED 로 바꿨고,
 * updateStatus 가 COMPLETED 에서 에스크로를 제공자에게 지급하므로
 * "아무도 완료를 누르지 않아도 예정일만 지나면 돈이 넘어가는" 상태였다.
 */
@SpringBootTest
class CareRequestSchedulerSettlementTest {

    private static final int AMOUNT = 1_000;

    @Autowired
    private CareRequestScheduler scheduler;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private CareApplicationRepository careApplicationRepository;

    @Autowired
    private PetCoinEscrowRepository escrowRepository;

    private Users requester;
    private Users provider;
    private CareRequest careRequest;
    private CareApplication application;
    private PetCoinEscrow escrow;

    @BeforeEach
    void setup() {
        long uniqueId = System.currentTimeMillis();

        requester = usersRepository.save(Users.builder()
                .id("sched_req_" + uniqueId).username("SchedReq_" + uniqueId)
                .email("sched_req_" + uniqueId + "@test.com").password("password123")
                .nickname("SchedReq_" + uniqueId).role(Role.USER).build());

        provider = usersRepository.save(Users.builder()
                .id("sched_prv_" + uniqueId).username("SchedPrv_" + uniqueId)
                .email("sched_prv_" + uniqueId + "@test.com").password("password123")
                .nickname("SchedPrv_" + uniqueId).role(Role.USER).build());
    }

    @AfterEach
    void tearDown() {
        if (careRequest != null) {
            careRequestRepository.deleteById(careRequest.getIdx());   // 에스크로·지원은 FK CASCADE
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
        if (provider != null) {
            usersRepository.deleteById(provider.getIdx());
        }
    }

    /** 예정일이 지난 진행 중 케어 + HOLD 에스크로를 만든다. */
    private void givenExpiredInProgressCareWithEscrow() {
        careRequest = careRequestRepository.save(CareRequest.builder()
                .user(requester)
                .title("Scheduler Settlement Test")
                .description("Test Content")
                .date(LocalDateTime.now().minusDays(1))      // 예정일이 지났다
                .status(CareRequestStatus.IN_PROGRESS)
                .offeredCoins(AMOUNT)
                .build());

        application = careApplicationRepository.saveAndFlush(CareApplication.builder()
                .careRequest(careRequest)
                .provider(provider)
                .status(CareApplicationStatus.ACCEPTED)
                .build());

        escrow = escrowRepository.save(PetCoinEscrow.builder()
                .careRequest(careRequest)
                .careApplication(application)
                .requester(requester)
                .provider(provider)
                .amount(AMOUNT)
                .status(EscrowStatus.HOLD)
                .build());
    }

    @Test
    @DisplayName("예정일이 지나도 진행 중인 케어를 자동 완료·지급하지 않는다")
    void 진행중_케어는_자동정산되지_않는다() {
        givenExpiredInProgressCareWithEscrow();
        Integer providerBalanceBefore = provider.getPetCoinBalance();

        scheduler.updateExpiredCareRequests();

        // 옛 코드는 여기서 COMPLETED 로 바꾸고 에스크로를 제공자에게 지급했다.
        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getStatus())
                .as("이행 여부는 당사자만 안다 — 스케줄러가 완료로 단정하면 안 된다")
                .isEqualTo(CareRequestStatus.IN_PROGRESS);

        assertThat(escrowRepository.findByCareRequest(careRequest).orElseThrow().getStatus())
                .as("보관 중인 돈이 아무 확인 없이 제공자에게 넘어가면 안 된다")
                .isEqualTo(EscrowStatus.HOLD);

        assertThat(usersRepository.findById(provider.getIdx()).orElseThrow().getPetCoinBalance())
                .isEqualTo(providerBalanceBefore);
    }

    @Test
    @DisplayName("예정일이 지난 모집 중 요청은 성사되지 않은 것이므로 취소된다")
    void 모집중_만료는_취소된다() {
        careRequest = careRequestRepository.save(CareRequest.builder()
                .user(requester)
                .title("Scheduler Expiry Test")
                .description("Test Content")
                .date(LocalDateTime.now().minusDays(1))
                .status(CareRequestStatus.OPEN)
                .offeredCoins(AMOUNT)
                .build());

        scheduler.updateExpiredCareRequests();

        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getStatus())
                .as("아무도 신청하지 않은 채 예정일이 지난 요청이 '완료'일 수는 없다")
                .isEqualTo(CareRequestStatus.CANCELLED);
    }
}
