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

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.exception.PetCoinEscrowNotFoundException;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 지급 대상 배정이 실패하면 예외가 호출자까지 그대로 올라와야 한다.
 *
 * <p>예전 확정 코드는 이 호출을 {@code try/catch} 로 삼키고 "확정은 진행한다"는 주석까지
 * 달아뒀다. 그런데 그 호출은 {@code REQUIRED} 로 같은 트랜잭션에 합류하므로 실패가
 * rollback-only 를 남긴다 — <strong>삼켜도 바깥 커밋에서 터질 뿐, 의도한 "배정 없는 확정"은
 * 애초에 만들어질 수 없었다.</strong> 예외를 잡는 것만으로 격리했다고 착각하게 되는 자리가
 * 트랜잭션 전파다.
 *
 * <p>전파로 바꾼 실효는 롤백을 만드는 게 아니라 <strong>원인을 남기는 것</strong>이었다
 * (원인 불명 500 → 원인이 담긴 응답). 계약이 채팅방에서 care 로 옮겨 온 뒤에도 같은 계약이
 * 지켜지는지를 이 테스트가 잠근다.
 */
@SpringBootTest
class CareOfferEscrowFailureTest {

    private static final int OFFERED_COINS = 1_000;

    @Autowired
    private CareOfferService careOfferService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private CareApplicationRepository careApplicationRepository;

    private Users requester;
    private Users provider;
    private CareRequest careRequest;
    private Long offerIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.nanoTime();

        // 요청을 서비스가 아니라 리포지토리로 직접 만든다 = 등록 시 에스크로를 잡지 않던 시절의 데이터.
        requester = usersRepository.save(Users.builder()
                .id("offerfail_req_" + uniqueId).username("OfferFailReq_" + uniqueId)
                .email("offerfail_req_" + uniqueId + "@test.com").password("password123")
                .nickname("OfferFailReq_" + uniqueId).role(Role.USER).build());

        provider = usersRepository.save(Users.builder()
                .id("offerfail_prv_" + uniqueId).username("OfferFailPrv_" + uniqueId)
                .email("offerfail_prv_" + uniqueId + "@test.com").password("password123")
                .nickname("OfferFailPrv_" + uniqueId).role(Role.SERVICE_PROVIDER).build());

        careRequest = careRequestRepository.save(CareRequest.builder()
                .user(requester)
                .title("Offer Escrow Failure Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .status(CareRequestStatus.OPEN)
                .offeredCoins(OFFERED_COINS)
                .build());

        // 제안도 리포지토리로 직접 만든다 — offerTo 를 타면 제안은 정상이지만, 여기서 재현하려는
        // 것은 "보관이 없는데 수락이 들어오는" 상황이라 제안 자체는 성립해 있어야 한다.
        offerIdx = careApplicationRepository.saveAndFlush(CareApplication.builder()
                .careRequest(careRequest)
                .provider(provider)
                .status(CareApplicationStatus.PENDING)
                .offeredCoins(OFFERED_COINS)
                .build()).getIdx();
    }

    @AfterEach
    void tearDown() {
        if (careRequest != null) {
            careRequestRepository.deleteById(careRequest.getIdx());
        }
        if (provider != null) {
            usersRepository.deleteById(provider.getIdx());
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
    }

    @Test
    @DisplayName("보관된 에스크로가 없으면, 원인 예외가 호출자까지 그대로 전파된다")
    void acceptOffer_whenEscrowMissing_propagatesCause() {
        assertThatThrownBy(() -> careOfferService.acceptOffer(offerIdx, provider.getIdx()))
                .as("원인이 다른 예외로 덮이면 호출자는 이유를 알 수 없다 (HTTP 500 이 나간다)")
                .isInstanceOf(PetCoinEscrowNotFoundException.class);

        // 보조 단언: 수락이 롤백돼 OPEN 으로 남는다.
        // 이 단언만으로는 수정 여부를 가리지 못한다(삼키는 코드도 롤백됐다) — 계약을 고정하는 용도다.
        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getStatus())
                .as("배정이 실패했는데 계약만 성립하면 제공자가 무보수로 일하게 된다")
                .isEqualTo(CareRequestStatus.OPEN);
        assertThat(careApplicationRepository.findById(offerIdx).orElseThrow().getStatus())
                .isEqualTo(CareApplicationStatus.PENDING);
    }
}
