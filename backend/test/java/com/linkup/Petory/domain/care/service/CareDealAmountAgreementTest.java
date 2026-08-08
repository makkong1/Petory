package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
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
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.chat.service.ConversationService;
import com.linkup.Petory.domain.payment.exception.PaymentConflictException;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 성립한 계약의 금액에 양쪽이 동의했음을 보장한다.
 *
 * 확정은 요청자와 제공자가 각자 따로 누른다. 금액은 OPEN 인 동안 수정할 수 있으므로, 아무 장치가
 * 없으면 이런 일이 생긴다.
 *
 *     제공자: 5,000 보고 확정
 *     요청자: 금액을 1,000 으로 수정
 *     요청자: 1,000 보고 확정  -> 계약 성립
 *             ㄴ 제공자의 동의는 5,000 에 대한 것이었다
 *
 * 두 장치가 각각 다른 것을 막는다.
 *   - expectedAmount : 지금 내가 화면에서 보고 동의하는 값이 실제와 같은가
 *   - 변경 시각 비교  : 이미 있는 동의가 마지막 금액 변경보다 이전인가(= 낡았는가)
 * 하나만으로는 위 시나리오가 막히지 않는다.
 */
@SpringBootTest
class CareDealAmountAgreementTest {

    private static final int INITIAL_BALANCE = 50_000;
    private static final int OFFERED = 5_000;

    @Autowired
    private CareRequestService careRequestService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    private Users requester;
    private Users provider;
    private Long careRequestIdx;
    private Conversation conversation;

    @BeforeEach
    void setup() {
        long uniqueId = System.currentTimeMillis();

        requester = usersRepository.save(Users.builder()
                .id("agr_req_" + uniqueId).username("AgrReq_" + uniqueId)
                .email("agr_req_" + uniqueId + "@test.com").password("password123")
                .nickname("AgrReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        provider = usersRepository.save(Users.builder()
                .id("agr_prv_" + uniqueId).username("AgrPrv_" + uniqueId)
                .email("agr_prv_" + uniqueId + "@test.com").password("password123")
                .nickname("AgrPrv_" + uniqueId).role(Role.USER).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Amount Agreement Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .offeredCoins(OFFERED)
                .latitude(37.5).longitude(127.0).address("서울시 어딘가")
                .build()).getIdx();

        conversation = conversationRepository.save(Conversation.builder()
                .conversationType(ConversationType.CARE_REQUEST)
                .relatedType(RelatedType.CARE_REQUEST)
                .relatedIdx(careRequestIdx)
                .status(ConversationStatus.ACTIVE)
                .build());

        for (Users u : new Users[] { requester, provider }) {
            participantRepository.save(ConversationParticipant.builder()
                    .conversation(conversation).user(u)
                    .role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE)
                    .dealConfirmed(false).build());
        }
    }

    @AfterEach
    void tearDown() {
        if (conversation != null) {
            List<ConversationParticipant> participants = participantRepository
                    .findByConversationIdxAndStatus(conversation.getIdx(), ParticipantStatus.ACTIVE);
            if (!participants.isEmpty()) {
                participantRepository.deleteAllInBatch(participants);
            }
            conversationRepository.deleteById(conversation.getIdx());
        }
        if (careRequestIdx != null) {
            careRequestRepository.deleteById(careRequestIdx);
        }
        for (Users u : new Users[] { requester, provider }) {
            if (u != null) {
                usersRepository.deleteById(u.getIdx());
            }
        }
    }

    private void changeAmountTo(int amount) {
        careRequestService.updateCareRequest(careRequestIdx,
                CareRequestDTO.builder().offeredCoins(amount).build(), requester.getIdx());
    }

    private boolean confirmedOf(Users user) {
        return Boolean.TRUE.equals(participantRepository
                .findByConversationIdxAndUserIdx(conversation.getIdx(), user.getIdx())
                .orElseThrow().getDealConfirmed());
    }

    @Test
    @DisplayName("화면에서 본 금액이 실제와 다르면 확정이 거절된다")
    void 본_금액이_다르면_거절() {
        changeAmountTo(1_000);

        assertThatThrownBy(() -> conversationService.confirmCareDeal(
                conversation.getIdx(), provider.getIdx(), OFFERED))
                        .as("화면엔 5,000 이 떠 있는데 1,000 짜리 계약이 성립하면 안 된다")
                        .isInstanceOf(PaymentConflictException.class);

        assertThat(confirmedOf(provider)).isFalse();
    }

    @Test
    @DisplayName("금액이 바뀌면 그 전에 한 확정은 무효가 되어 다시 받아야 한다")
    void 금액_변경시_옛_확정_무효화() {
        // 제공자가 5,000 을 보고 먼저 확정
        conversationService.confirmCareDeal(conversation.getIdx(), provider.getIdx(), OFFERED);
        assertThat(confirmedOf(provider)).isTrue();

        // 요청자가 금액을 내린 뒤 새 금액으로 확정을 시도한다
        changeAmountTo(1_000);
        conversationService.confirmCareDeal(conversation.getIdx(), requester.getIdx(), 1_000);

        // 제공자의 동의는 5,000 에 대한 것이었으므로 무효화됐어야 한다 = 아직 계약 미성립
        assertThat(confirmedOf(provider))
                .as("서로 다른 금액에 동의한 채로 계약이 성립하면 안 된다")
                .isFalse();
        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow().getStatus().name())
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName("금액이 그대로면 양쪽 확정으로 계약이 성립하고 지급 대상이 배정된다")
    void 금액_변경_없으면_정상_성립() {
        conversationService.confirmCareDeal(conversation.getIdx(), provider.getIdx(), OFFERED);
        conversationService.confirmCareDeal(conversation.getIdx(), requester.getIdx(), OFFERED);

        assertThat(careRequestRepository.findById(careRequestIdx).orElseThrow().getStatus().name())
                .isEqualTo("IN_PROGRESS");
    }
}
