package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 케어 계약이 제안 → 수락으로 성립하는 경로를 고정한다.
 *
 * <p>예전에는 계약이 채팅방에 묶여 있었다. 양쪽이 같은 "거래 확정" 버튼을 각자 누르는 대칭
 * 구조였고, 계약의 식별자가 방이었다. 그래서 방 타입이 실제로 만들어지는 것과 어긋나자
 * <strong>확정 버튼을 눌러도 아무 일도 일어나지 않는</strong> 상태가 됐다 — 화면만 진행되고
 * 지원 상태·요청 상태·에스크로는 그대로였다.
 *
 * <p>지금은 계약이 care 안에만 있다. 이 테스트는 그 경로가 실제로 도는지, 선정되지 않은
 * 제안은 어떻게 되는지, 그리고 <strong>진행 중으로 가는 길이 수락 하나뿐인지</strong>를 잠근다.
 * 마지막 항목이 중요한 이유: 다른 길로 진행 중이 되면 에스크로에 지급 대상이 비어 있는 채로
 * 계약이 진행되고, 정산 시점에 가서야 터진다.
 */
@SpringBootTest
class CareOfferContractTest {

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
                .id("offer_req_" + uniqueId).username("OfferReq_" + uniqueId)
                .email("offer_req_" + uniqueId + "@test.com").password("password123")
                .nickname("OfferReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Offer Contract Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .offeredCoins(OFFERED)
                .latitude(37.5).longitude(127.0).address("서울시 어딘가")
                .build()).getIdx();

        // 요청자가 제공자 두 명에게 나란히 제안한다. 먼저 수락한 쪽이 계약자가 된다.
        for (int i = 0; i < 2; i++) {
            Users provider = usersRepository.save(Users.builder()
                    .id("offer_prv" + i + "_" + uniqueId).username("OfferPrv" + i + "_" + uniqueId)
                    .email("offer_prv" + i + "_" + uniqueId + "@test.com").password("password123")
                    .nickname("OfferPrv" + i + "_" + uniqueId).role(Role.SERVICE_PROVIDER)
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

    private CareApplicationStatus statusOf(int index) {
        return careApplicationRepository.findById(offerIds.get(index)).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("제공자가 수락하면 지원이 승인되고 요청이 진행 중으로 넘어가며 지급 대상이 배정된다")
    void 수락하면_도메인이_실제로_움직인다() {
        careOfferService.acceptOffer(offerIds.get(0), providers.get(0).getIdx());

        assertThat(statusOf(0))
                .as("수락했는데 제안이 대기 중으로 남으면 계약이 성립한 게 아니다")
                .isEqualTo(CareApplicationStatus.ACCEPTED);

        var request = careRequestRepository.findById(careRequestIdx).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);

        var escrow = escrowRepository.findByCareRequest(request).orElseThrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.HOLD);
        assertThat(escrow.getProvider())
                .as("지급 대상이 비어 있으면 완료 시점에 줄 사람이 없다")
                .isNotNull();
        assertThat(escrow.getProvider().getIdx()).isEqualTo(providers.get(0).getIdx());
    }

    @Test
    @DisplayName("한 제안이 수락되면 나머지 제안은 선정되지 않은 것으로 정리된다")
    void 수락되면_나머지_제안은_REJECTED() {
        careOfferService.acceptOffer(offerIds.get(0), providers.get(0).getIdx());

        assertThat(statusOf(1))
                .as("대기 중으로 두면 그 제공자는 계속 답을 기다리는 줄 안다")
                .isEqualTo(CareApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("이미 확정된 요청의 다른 제안을 수락하면 이유를 알려주고 거절한다")
    void 이미_확정된_요청은_다른_제안_수락_불가() {
        careOfferService.acceptOffer(offerIds.get(0), providers.get(0).getIdx());

        assertThatThrownBy(
                () -> careOfferService.acceptOffer(offerIds.get(1), providers.get(1).getIdx()))
                        .as("조용히 넘어가면 제공자는 자기가 맡은 줄 알고 준비한다")
                        .isInstanceOf(CareConflictException.class);
    }

    @Test
    @DisplayName("진행 중으로 가는 길은 수락 하나뿐 — 상태 변경 API 로는 넘어갈 수 없다")
    void 진행중_수동_전이_차단() {
        assertThatThrownBy(() -> careRequestService.updateStatus(
                careRequestIdx, CareRequestStatus.IN_PROGRESS.name(), requester.getIdx()))
                        .as("지급 대상 없이 진행 중이 되면 정산 시점에 줄 사람이 없어 터진다")
                        .isInstanceOf(IllegalStateException.class);

        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow().getStatus())
                .isEqualTo(CareRequestStatus.OPEN);
    }
}
