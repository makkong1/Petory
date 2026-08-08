package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 끝난 거래의 조건이 사후에 바뀌지 않도록 고정한다.
 *
 * 이전에는 작성자 확인만 있고 상태 가드가 없어서, 이미 COMPLETED 된 케어의 날짜·장소·펫을
 * 요청자가 나중에 바꿀 수 있었다. 정산이 끝난 거래의 계약 조건이 바뀌는 셈이다.
 */
@SpringBootTest
class CareRequestUpdateGuardTest {

    @Autowired
    private CareRequestService careRequestService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    private Users requester;
    private CareRequest careRequest;

    @BeforeEach
    void setup() {
        long uniqueId = System.currentTimeMillis();
        requester = usersRepository.save(Users.builder()
                .id("updguard_" + uniqueId).username("UpdGuard_" + uniqueId)
                .email("updguard_" + uniqueId + "@test.com").password("password123")
                .nickname("UpdGuard_" + uniqueId).role(Role.USER).build());
    }

    @AfterEach
    void tearDown() {
        if (careRequest != null) {
            careRequestRepository.deleteById(careRequest.getIdx());
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
    }

    private void givenCareRequestWith(CareRequestStatus status) {
        careRequest = careRequestRepository.save(CareRequest.builder()
                .user(requester)
                .title("원래 제목")
                .description("원래 설명")
                .date(LocalDateTime.now().plusDays(1))
                .status(status)
                .offeredCoins(1_000)
                .build());
    }

    @Test
    @DisplayName("완료된 케어의 조건은 사후에 바꿀 수 없다")
    void 완료된_케어는_수정_불가() {
        givenCareRequestWith(CareRequestStatus.COMPLETED);
        CareRequestDTO patch = CareRequestDTO.builder().title("몰래 바꾼 제목").build();

        assertThatThrownBy(
                () -> careRequestService.updateCareRequest(careRequest.getIdx(), patch, requester.getIdx()))
                        .as("정산까지 끝난 거래의 계약 조건이 바뀌면 안 된다")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("OPEN");

        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getTitle())
                .isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("진행 중인 케어도 수정할 수 없다 (제공자가 보고 수락한 조건이다)")
    void 진행중_케어는_수정_불가() {
        givenCareRequestWith(CareRequestStatus.IN_PROGRESS);
        CareRequestDTO patch = CareRequestDTO.builder().title("몰래 바꾼 제목").build();

        assertThatThrownBy(
                () -> careRequestService.updateCareRequest(careRequest.getIdx(), patch, requester.getIdx()))
                        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("모집 중이면 수정된다")
    void 모집중이면_수정_가능() {
        givenCareRequestWith(CareRequestStatus.OPEN);
        CareRequestDTO patch = CareRequestDTO.builder().title("고친 제목").build();

        assertThatCode(
                () -> careRequestService.updateCareRequest(careRequest.getIdx(), patch, requester.getIdx()))
                        .doesNotThrowAnyException();

        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getTitle())
                .isEqualTo("고친 제목");
    }
}
