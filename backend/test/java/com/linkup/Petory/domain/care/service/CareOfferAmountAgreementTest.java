package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.exception.CareConflictException;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 지키려는 불변식: <strong>성립한 계약의 동의는 실제 금액에 대한 것이다.</strong>
 *
 * <p>대칭 확정(양쪽이 각자 누름)일 때는 동의가 두 개였고, 그 사이에 금액이 바뀌면 서로 다른
 * 금액에 동의한 채 계약이 성립할 수 있었다. 그래서 확정할 때마다 기존 동의를 현재 금액과 대조해
 * 무효화하는 로직이 필요했다(한때는 확정 시각과 금액 변경 시각을 비교했는데, 둘 다 초 단위라
 * 같은 초에 일어나면 구분하지 못해 샜다 — V15 가 그걸 금액 자체를 기록하는 방식으로 고쳤다).
 *
 * <p>제안/수락은 동의가 한 번뿐이다. 제안이 그 시점의 금액을 들고 있고, 제공자는 그 금액을 보고
 * 수락한다. 그래서 무효화 로직 없이 <strong>수락 시점 대조 한 번</strong>으로 불변식이 성립한다.
 * 이 테스트는 그 한 번이 실제로 막는지를 잠근다.
 */
@SpringBootTest
class CareOfferAmountAgreementTest {

    private static final int INITIAL_BALANCE = 50_000;
    private static final int OFFERED = 5_000;
    private static final int CHANGED = 1_000;

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
    private Users provider;
    private Long careRequestIdx;
    private Long offerIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.nanoTime();

        requester = usersRepository.save(Users.builder()
                .id("amt_req_" + uniqueId).username("AmtReq_" + uniqueId)
                .email("amt_req_" + uniqueId + "@test.com").password("password123")
                .nickname("AmtReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        provider = usersRepository.save(Users.builder()
                .id("amt_prv_" + uniqueId).username("AmtPrv_" + uniqueId)
                .email("amt_prv_" + uniqueId + "@test.com").password("password123")
                .nickname("AmtPrv_" + uniqueId).role(Role.SERVICE_PROVIDER)
                .emailVerified(true).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Amount Agreement Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .offeredCoins(OFFERED)
                .latitude(37.5).longitude(127.0).address("서울시 어딘가")
                .build()).getIdx();

        offerIdx = careOfferService
                .offerTo(careRequestIdx, provider.getIdx(), requester.getIdx())
                .getIdx();
    }

    @AfterEach
    void tearDown() {
        if (careRequestIdx != null) {
            careRequestRepository.deleteById(careRequestIdx);   // 에스크로는 FK CASCADE
        }
        if (provider != null) {
            usersRepository.deleteById(provider.getIdx());
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
    }

    /**
     * 금액만 바꾼다 — 제안을 철회하지 않고.
     *
     * 정상 경로({@code updateCareRequest})는 금액이 바뀌면 나가 있던 제안을 철회하므로
     * 이 어긋남이 화면에 드러나지 않는다. 그런데 {@code updateCareRequest} 는 요청 행을 잠그지
     * 않아서, 금액 변경과 수락이 겹치면 <b>철회를 지나친 제안이 수락에 도달할 수 있다.</b>
     * 그 경쟁을 재현하려고 철회를 건너뛰고 금액만 바꾼다.
     */
    private void changeAmountWithoutWithdrawing(int amount) {
        var request = careRequestRepository.findById(careRequestIdx).orElseThrow();
        request.changeOfferedCoins(amount);
        careRequestRepository.save(request);
    }

    @Test
    @DisplayName("제안 금액과 현재 금액이 어긋난 채 수락이 도달하면 거절된다 (마지막 안전망)")
    void 금액이_어긋난_수락은_거절() {
        // Given: 제공자는 5,000 을 보고 있는데
        assertThat(careApplicationRepository.findById(offerIdx).orElseThrow().getOfferedCoins())
                .isEqualTo(OFFERED);

        // When: 철회를 지나쳐 금액만 1,000 으로 바뀌었다(경쟁 재현)
        changeAmountWithoutWithdrawing(CHANGED);

        // Then: 수락 시점 대조가 막는다
        assertThatThrownBy(() -> careOfferService.acceptOffer(offerIdx, provider.getIdx()))
                .as("제공자가 5,000 인 줄 알고 맡았는데 1,000 이 지급되면 안 된다")
                .isInstanceOf(CareConflictException.class);

        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow().getStatus())
                .as("수락이 막혔는데 요청만 진행 중이 되면 안 된다")
                .isEqualTo(CareRequestStatus.OPEN);
        assertThat(careApplicationRepository.findById(offerIdx).orElseThrow().getStatus())
                .isEqualTo(CareApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("금액이 그대로면 수락으로 계약이 성립하고 그 금액으로 지급 대상이 배정된다")
    void 금액_변경_없으면_정상_성립() {
        careOfferService.acceptOffer(offerIdx, provider.getIdx());

        var request = careRequestRepository.findById(careRequestIdx).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);

        var escrow = escrowRepository.findByCareRequest(request).orElseThrow();
        assertThat(escrow.getProvider()).isNotNull();
        assertThat(escrow.getAmount())
                .as("보관 금액과 동의한 금액이 다르면 계약이 성립한 게 아니다")
                .isEqualTo(OFFERED);
    }
}
