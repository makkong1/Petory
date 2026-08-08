package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.linkup.Petory.domain.care.exception.CareForbiddenException;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 정산은 양쪽이 이행을 확인해야만 일어난다는 것을 고정한다.
 *
 * 예전에는 updateStatus 를 요청자 "또는" 승인된 제공자 아무나 호출할 수 있었고, COMPLETED 가
 * 되는 순간 에스크로가 제공자에게 지급됐다. 즉 제공자가 혼자 완료를 눌러 요청자 동의 없이
 * 돈을 가져갈 수 있었다. 프론트에서도 완료 버튼이 제공자에게만 보였다.
 */
@SpringBootTest
class CareCompletionConfirmationTest {

    private static final int AMOUNT = 1_000;

    @Autowired
    private CareRequestService careRequestService;

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
    private Users stranger;
    private CareRequest careRequest;

    @BeforeEach
    void setup() {
        long uniqueId = System.currentTimeMillis();

        requester = createUser("cmp_req_" + uniqueId);
        provider = createUser("cmp_prv_" + uniqueId);
        stranger = createUser("cmp_str_" + uniqueId);

        careRequest = careRequestRepository.save(CareRequest.builder()
                .user(requester)
                .title("Completion Confirmation Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .status(CareRequestStatus.IN_PROGRESS)
                .offeredCoins(AMOUNT)
                .build());

        CareApplication application = careApplicationRepository.saveAndFlush(CareApplication.builder()
                .careRequest(careRequest)
                .provider(provider)
                .status(CareApplicationStatus.ACCEPTED)
                .build());

        escrowRepository.save(PetCoinEscrow.builder()
                .careRequest(careRequest)
                .careApplication(application)
                .requester(requester)
                .provider(provider)
                .amount(AMOUNT)
                .status(EscrowStatus.HOLD)
                .build());
    }

    private Users createUser(String key) {
        return usersRepository.save(Users.builder()
                .id(key).username("U_" + key).email(key + "@test.com")
                .password("password123").nickname("N_" + key).role(Role.USER).build());
    }

    @AfterEach
    void tearDown() {
        if (careRequest != null) {
            careRequestRepository.deleteById(careRequest.getIdx());   // 지원·에스크로는 FK CASCADE
        }
        for (Users u : new Users[] { requester, provider, stranger }) {
            if (u != null) {
                usersRepository.deleteById(u.getIdx());
            }
        }
    }

    private EscrowStatus escrowStatus() {
        return escrowRepository.findByCareRequest(careRequest).orElseThrow().getStatus();
    }

    private Integer balanceOf(Users user) {
        return usersRepository.findById(user.getIdx()).orElseThrow().getPetCoinBalance();
    }

    private CareRequestStatus reloadStatus() {
        return careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("제공자 혼자 확인해서는 정산되지 않는다")
    void 제공자_단독확인은_정산되지_않는다() {
        careRequestService.confirmCompletion(careRequest.getIdx(), provider.getIdx());

        assertThat(reloadStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);
        assertThat(escrowStatus())
                .as("요청자 확인 없이 보관 중인 돈이 넘어가면 안 된다")
                .isEqualTo(EscrowStatus.HOLD);
        assertThat(balanceOf(provider)).isZero();
    }

    @Test
    @DisplayName("같은 쪽이 두 번 눌러도 정산되지 않는다 (재시도 안전)")
    void 같은쪽_재확인은_정산되지_않는다() {
        careRequestService.confirmCompletion(careRequest.getIdx(), provider.getIdx());
        careRequestService.confirmCompletion(careRequest.getIdx(), provider.getIdx());

        assertThat(reloadStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);
        assertThat(escrowStatus()).isEqualTo(EscrowStatus.HOLD);
    }

    @Test
    @DisplayName("양쪽이 확인하면 완료되고 제공자에게 지급된다")
    void 양쪽_확인시_정산된다() {
        careRequestService.confirmCompletion(careRequest.getIdx(), provider.getIdx());
        careRequestService.confirmCompletion(careRequest.getIdx(), requester.getIdx());

        assertThat(reloadStatus()).isEqualTo(CareRequestStatus.COMPLETED);
        assertThat(escrowStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(balanceOf(provider)).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("당사자가 아니면 완료를 확인할 수 없다")
    void 제3자는_확인할_수_없다() {
        assertThatThrownBy(
                () -> careRequestService.confirmCompletion(careRequest.getIdx(), stranger.getIdx()))
                        .isInstanceOf(CareForbiddenException.class);

        assertThat(escrowStatus()).isEqualTo(EscrowStatus.HOLD);
    }

    @Test
    @DisplayName("제공자가 혼자 상태를 COMPLETED 로 바꿔 정산할 수 없다 (옛 경로 차단)")
    void 제공자_단독_상태변경으로는_정산할_수_없다() {
        assertThatThrownBy(
                () -> careRequestService.updateStatus(careRequest.getIdx(), "COMPLETED", provider.getIdx()))
                        .as("이 경로가 열려 있던 동안 제공자는 요청자 동의 없이 돈을 가져갈 수 있었다")
                        .isInstanceOf(CareForbiddenException.class);

        assertThat(reloadStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);
        assertThat(escrowStatus()).isEqualTo(EscrowStatus.HOLD);
        assertThat(balanceOf(provider)).isZero();
    }

    @Test
    @DisplayName("양쪽이 동시에 확인해도 지급은 한 번만 일어난다")
    void 동시_확인시_이중지급_없음() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        for (Long userId : new Long[] { requester.getIdx(), provider.getIdx() }) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    careRequestService.confirmCompletion(careRequest.getIdx(), userId);
                } catch (Exception e) {
                    // 락 경합으로 한쪽이 실패해도 무방하다 — 검증 대상은 최종 상태다.
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(escrowStatus())
                .as("두 요청이 각자 정산으로 넘어가면 이중 지급이 된다")
                .isEqualTo(EscrowStatus.RELEASED);
        assertThat(balanceOf(provider))
                .as("지급은 정확히 한 번")
                .isEqualTo(AMOUNT);
    }
}
