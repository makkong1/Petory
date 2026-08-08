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
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.exception.InsufficientBalanceException;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 제시 금액이 "등록 시점"에 잡힌다는 것을 고정한다.
 *
 * 예전에는 createCareRequest 가 잔액을 확인만 하고 잡지는 않았다(TOCTOU). 확인과 실제 차감(거래 확정)
 * 사이에 잔액을 다른 데 쓰면 확정 순간에 깨졌고, 제공자는 신청하고 채팅까지 마친 뒤에야 그 사실을 알았다.
 * 등록에서 잡아야 "올라와 있는 요청은 지급이 보증된다"가 성립한다.
 *
 * 금액 수정은 목표 금액(증분 아님)을 받아 차액만 정산하므로, 같은 요청이 두 번 도착해도 두 번째는
 * 차액이 0 이 되어 아무 일도 일어나지 않는다 — 멱등키 없이 재시도 안전한 이유가 이 표현 방식에 있다.
 */
@SpringBootTest
class CareEscrowAtCreationTest {

    private static final int INITIAL_BALANCE = 10_000;
    private static final int OFFERED = 3_000;

    @Autowired
    private CareRequestService careRequestService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private PetCoinEscrowRepository escrowRepository;

    private Users requester;
    private Long careRequestIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.currentTimeMillis();
        requester = usersRepository.save(Users.builder()
                .id("hold_req_" + uniqueId).username("HoldReq_" + uniqueId)
                .email("hold_req_" + uniqueId + "@test.com").password("password123")
                .nickname("HoldReq_" + uniqueId).role(Role.USER)
                .emailVerified(true)
                .petCoinBalance(INITIAL_BALANCE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (careRequestIdx != null) {
            careRequestRepository.deleteById(careRequestIdx);   // 에스크로는 FK CASCADE
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
    }

    private CareRequestDTO newRequestDto(int offeredCoins) {
        return CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Escrow At Creation Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .offeredCoins(offeredCoins)
                .latitude(37.5)
                .longitude(127.0)
                .address("서울시 어딘가")
                .build();
    }

    private Long createRequest(int offeredCoins) {
        careRequestIdx = careRequestService.createCareRequest(newRequestDto(offeredCoins)).getIdx();
        return careRequestIdx;
    }

    private PetCoinEscrow escrow() {
        return escrowRepository
                .findByCareRequest(careRequestRepository.findById(careRequestIdx).orElseThrow())
                .orElseThrow();
    }

    private Integer balance() {
        return usersRepository.findById(requester.getIdx()).orElseThrow().getPetCoinBalance();
    }

    @Test
    @DisplayName("요청을 등록하면 그 자리에서 코인이 차감되고 상대 없는 보관이 생긴다")
    void 등록_시_보관된다() {
        createRequest(OFFERED);

        assertThat(balance())
                .as("등록 시점에 잡지 않으면 확정 때 잔액이 없을 수 있다")
                .isEqualTo(INITIAL_BALANCE - OFFERED);

        PetCoinEscrow escrow = escrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.HOLD);
        assertThat(escrow.getAmount()).isEqualTo(OFFERED);
        assertThat(escrow.isUnassigned())
                .as("등록 시점에는 상대가 정해지지 않았다")
                .isTrue();
    }

    @Test
    @DisplayName("잔액이 모자라면 요청 자체가 등록되지 않는다")
    void 잔액부족이면_등록되지_않는다() {
        assertThatThrownBy(() -> createRequest(INITIAL_BALANCE + 1))
                .isInstanceOf(InsufficientBalanceException.class);

        careRequestIdx = null;   // 롤백돼 남은 게 없다
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    @DisplayName("금액을 올리면 차액만큼 더 잡힌다")
    void 증액하면_차액만큼_추가차감() {
        createRequest(OFFERED);

        careRequestService.updateCareRequest(careRequestIdx,
                CareRequestDTO.builder().offeredCoins(OFFERED + 2_000).build(), requester.getIdx());

        assertThat(escrow().getAmount()).isEqualTo(OFFERED + 2_000);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - (OFFERED + 2_000));
    }

    @Test
    @DisplayName("금액을 내리면 차액만큼 돌려받는다")
    void 감액하면_차액만큼_환불() {
        createRequest(OFFERED);

        careRequestService.updateCareRequest(careRequestIdx,
                CareRequestDTO.builder().offeredCoins(1_000).build(), requester.getIdx());

        assertThat(escrow().getAmount()).isEqualTo(1_000);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - 1_000);
    }

    @Test
    @DisplayName("같은 금액으로 두 번 수정해도 결과가 같다 (목표 금액 방식이라 멱등)")
    void 같은_금액_반복수정은_멱등() {
        createRequest(OFFERED);

        for (int i = 0; i < 2; i++) {
            careRequestService.updateCareRequest(careRequestIdx,
                    CareRequestDTO.builder().offeredCoins(5_000).build(), requester.getIdx());
        }

        assertThat(escrow().getAmount()).isEqualTo(5_000);
        assertThat(balance())
                .as("증분 방식이었다면 두 번째 호출에서 또 차감돼 값이 어긋난다")
                .isEqualTo(INITIAL_BALANCE - 5_000);
    }

    @Test
    @DisplayName("금액을 바꾸면 변경 시각이 남는다 (낡은 거래 확정을 가려내는 근거)")
    void 금액_변경_시각이_남는다() {
        createRequest(OFFERED);
        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow()
                .getOfferedCoinsUpdatedAt()).isNull();

        careRequestService.updateCareRequest(careRequestIdx,
                CareRequestDTO.builder().offeredCoins(4_000).build(), requester.getIdx());

        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow()
                .getOfferedCoinsUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("요청을 지우면 잡아둔 코인이 돌아온다")
    void 삭제하면_환불된다() {
        createRequest(OFFERED);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - OFFERED);

        careRequestService.deleteCareRequest(careRequestIdx, requester.getIdx());

        assertThat(balance())
                .as("내부 코인은 플랫폼이 돌려주지 않으면 회수 수단이 없다")
                .isEqualTo(INITIAL_BALANCE);
        assertThat(escrow().getStatus()).isEqualTo(EscrowStatus.REFUNDED);
    }

    @Test
    @DisplayName("진행 중인 요청은 지울 수 없다")
    void 진행중_요청은_삭제_불가() {
        createRequest(OFFERED);
        var request = careRequestRepository.findById(careRequestIdx).orElseThrow();
        request.transitionTo(CareRequestStatus.IN_PROGRESS);
        careRequestRepository.save(request);

        assertThatThrownBy(() -> careRequestService.deleteCareRequest(careRequestIdx, requester.getIdx()))
                .as("상대가 있는 계약을 한쪽이 지우면 보관 코인의 귀속이 사라진다")
                .isInstanceOf(IllegalStateException.class);

        assertThat(escrow().getStatus()).isEqualTo(EscrowStatus.HOLD);
    }
}
