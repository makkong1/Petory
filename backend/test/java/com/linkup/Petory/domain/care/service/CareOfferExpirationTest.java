package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;


/**
 * 답 없이 방치된 제안이 3일 뒤 만료되는지.
 *
 * <p>3일을 기다릴 수 없으므로 {@code updated_at} 을 과거로 밀어 재현한다. 그 컬럼이 만료
 * 판정 기준이라, 여기를 조작하는 것이 곧 "3일이 지난 상태"다.
 */
@SpringBootTest
class CareOfferExpirationTest {

    private static final int INITIAL_BALANCE = 50_000;
    private static final int OFFERED = 5_000;

    @Autowired
    private CareRequestService careRequestService;

    @Autowired
    private CareOfferService careOfferService;

    @Autowired
    private CareOfferExpirationScheduler scheduler;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private CareApplicationRepository careApplicationRepository;

    // JPA 가 아니라 JdbcTemplate 을 쓰는 이유: 테스트 자체를 트랜잭션으로 감싸지 않기
    // 때문이다(스케줄러가 커밋된 데이터를 봐야 한다). 네이티브 JPQL update 는 트랜잭션을
    // 요구해서 여기선 못 쓴다.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Users requester;
    private Users provider;
    private Long careRequestIdx;
    private Long offerIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.nanoTime();

        requester = usersRepository.save(Users.builder()
                .id("exp_req_" + uniqueId).username("ExpReq_" + uniqueId)
                .email("exp_req_" + uniqueId + "@test.com").password("password123")
                .nickname("ExpReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        provider = usersRepository.save(Users.builder()
                .id("exp_prv_" + uniqueId).username("ExpPrv_" + uniqueId)
                .email("exp_prv_" + uniqueId + "@test.com").password("password123")
                .nickname("ExpPrv_" + uniqueId).role(Role.SERVICE_PROVIDER)
                .emailVerified(true).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Offer Expiration Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(10))
                .offeredCoins(OFFERED)
                .latitude(37.5).longitude(127.0).address("서울 강남구 역삼동")
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

    /** 제안이 보내진 지 지정한 일수만큼 지난 상태로 만든다. */
    private void ageOfferByDays(int days) {
        jdbcTemplate.update("UPDATE careapplication SET updated_at = ? WHERE idx = ?",
                LocalDateTime.now().minusDays(days), offerIdx);
    }

    private CareApplicationStatus statusOf() {
        return careApplicationRepository.findById(offerIdx).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("3일이 지나도록 답이 없으면 제안이 만료된다")
    void 기간이_지나면_만료() {
        ageOfferByDays(4);

        scheduler.expireStaleOffers();

        assertThat(statusOf())
                .as("거절(제공자 행동)도 철회(요청자 행동)도 아니다 — 아무도 아무것도 안 했다")
                .isEqualTo(CareApplicationStatus.EXPIRED);
    }

    @Test
    @DisplayName("기간 안이면 건드리지 않는다")
    void 기간_안에는_그대로() {
        ageOfferByDays(1);

        scheduler.expireStaleOffers();

        assertThat(statusOf()).isEqualTo(CareApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("만료돼도 케어 요청은 모집 중 그대로다")
    void 요청은_닫히지_않는다() {
        ageOfferByDays(4);

        scheduler.expireStaleOffers();

        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow().getStatus())
                .as("제안이 만료됐다고 요청까지 닫으면 요청자가 원하지 않은 취소가 된다")
                .isEqualTo(CareRequestStatus.OPEN);
    }

    @Test
    @DisplayName("만료된 제안은 같은 사람에게 다시 보낼 수 있다")
    void 만료_후_재제안() {
        ageOfferByDays(4);
        scheduler.expireStaleOffers();

        var resent = careOfferService.offerTo(careRequestIdx, provider.getIdx(), requester.getIdx());

        assertThat(resent.getIdx()).isEqualTo(offerIdx);
        assertThat(statusOf())
                .as("만료로 끝난 제안이 되살아나지 않으면 그 제공자에게는 영영 다시 못 보낸다")
                .isEqualTo(CareApplicationStatus.PENDING);
    }
}
