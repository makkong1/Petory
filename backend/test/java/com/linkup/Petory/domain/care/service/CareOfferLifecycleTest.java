package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;

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
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 제안이 끝나는 길과 다시 시작되는 길을 고정한다. 전부 리뷰에서 찾은 구멍이다.
 *
 * <ul>
 *   <li><b>거절 후 재제안</b> — 같은 제공자에게 다시 제안하는 경로가 막혀 있었다.
 *       {@code UNIQUE(care_request_idx, provider_idx)} 때문에 새 행을 만들 수 없는데 기존
 *       행을 되살리지도 않아서, 화면엔 "제안을 보냈습니다"가 뜨는데 실제로는 아무 일도
 *       일어나지 않았다.</li>
 *   <li><b>금액 변경</b> — 나가 있는 제안이 바뀌기 전 금액을 계속 들고 있었다. 제공자는 옛
 *       금액을 보다가 수락하는 순간에야 거절당했다.</li>
 *   <li><b>요청 취소·삭제</b> — 제안이 그대로 남아 제공자 화면에서 조용히 사라졌다.
 *       거절당한 건지 취소된 건지 알 수 없었다.</li>
 * </ul>
 *
 * 마지막 둘은 제공자가 아무것도 하지 않았는데 끝난 경우라 {@code REJECTED}(제공자가 거절)가
 * 아니라 {@code WITHDRAWN}(요청자 사정으로 철회)으로 적힌다 — 상태가 거짓말하지 않게.
 */
@SpringBootTest
class CareOfferLifecycleTest {

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

    private Users requester;
    private Users provider;
    private Long careRequestIdx;
    private Long offerIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.nanoTime();

        requester = usersRepository.save(Users.builder()
                .id("life_req_" + uniqueId).username("LifeReq_" + uniqueId)
                .email("life_req_" + uniqueId + "@test.com").password("password123")
                .nickname("LifeReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        provider = usersRepository.save(Users.builder()
                .id("life_prv_" + uniqueId).username("LifePrv_" + uniqueId)
                .email("life_prv_" + uniqueId + "@test.com").password("password123")
                .nickname("LifePrv_" + uniqueId).role(Role.SERVICE_PROVIDER)
                .emailVerified(true).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Offer Lifecycle Test")
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

    private CareApplicationStatus offerStatus() {
        return careApplicationRepository.findById(offerIdx).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("거절당한 제안도 다시 보낼 수 있고, 제공자에게 대기 중으로 보인다")
    void 거절_후_재제안() {
        careOfferService.rejectOffer(offerIdx, provider.getIdx());
        assertThat(offerStatus()).isEqualTo(CareApplicationStatus.REJECTED);

        var resent = careOfferService.offerTo(careRequestIdx, provider.getIdx(), requester.getIdx());

        assertThat(resent.getIdx())
                .as("UNIQUE 제약 때문에 새 행을 만들 수 없으므로 같은 행을 되살려야 한다")
                .isEqualTo(offerIdx);
        assertThat(offerStatus())
                .as("되살리지 않으면 화면엔 보냈다고 뜨는데 제공자에겐 아무것도 안 간다")
                .isEqualTo(CareApplicationStatus.PENDING);

        assertThat(careOfferService.findLiveOffersBetween(provider.getIdx(), requester.getIdx()))
                .as("제공자 쪽 조회에 다시 잡혀야 수락 버튼이 뜬다")
                .hasSize(1);
    }

    @Test
    @DisplayName("제시 금액이 바뀌면 나가 있던 제안은 철회된다 (거절이 아니라 철회다)")
    void 금액_변경시_제안_철회() {
        careRequestService.updateCareRequest(careRequestIdx,
                CareRequestDTO.builder().offeredCoins(1_000).build(), requester.getIdx());

        assertThat(offerStatus())
                .as("제공자는 아무것도 안 했으므로 REJECTED 로 적으면 상태가 거짓말을 한다")
                .isEqualTo(CareApplicationStatus.WITHDRAWN);

        // 새 금액으로 다시 제안할 수 있어야 한다
        var resent = careOfferService.offerTo(careRequestIdx, provider.getIdx(), requester.getIdx());
        assertThat(resent.getOfferedCoins()).isEqualTo(1_000);
        assertThat(offerStatus()).isEqualTo(CareApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("요청이 취소되면 나가 있던 제안도 철회된다")
    void 취소시_제안_철회() {
        careRequestService.updateStatus(
                careRequestIdx, CareRequestStatus.CANCELLED.name(), requester.getIdx());

        assertThat(offerStatus()).isEqualTo(CareApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("요청이 삭제되면 나가 있던 제안도 철회된다")
    void 삭제시_제안_철회() {
        careRequestService.deleteCareRequest(careRequestIdx, requester.getIdx());

        assertThat(offerStatus()).isEqualTo(CareApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("완료된 계약도 조회에 남는다 — 완료 배너와 리뷰 버튼이 거기 달려 있다")
    void 완료된_계약도_조회에_남는다() {
        careOfferService.acceptOffer(offerIdx, provider.getIdx());
        careRequestService.confirmCompletion(careRequestIdx, provider.getIdx());
        careRequestService.confirmCompletion(careRequestIdx, requester.getIdx());

        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow().getStatus())
                .isEqualTo(CareRequestStatus.COMPLETED);

        var live = careOfferService.findLiveOffersBetween(requester.getIdx(), provider.getIdx());
        assertThat(live)
                .as("여기서 빠지면 완료 배너도 리뷰 작성 버튼도 영영 뜨지 않는다")
                .hasSize(1);
        assertThat(live.get(0).getCareRequestStatus()).isEqualTo("COMPLETED");
        assertThat(live.get(0).getRequesterCompletedAt())
                .as("화면이 '내가 이미 확인했는지'를 그리려면 이 값이 실려야 한다")
                .isNotNull();
    }
}
