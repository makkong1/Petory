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

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
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
import com.linkup.Petory.domain.payment.exception.InsufficientBalanceException;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 거래 확정 시 펫코인 차감이 실패하는 경로를 고정한다.
 *
 * 왜 상태가 아니라 예외를 단언하는가: 예전 코드는 createEscrow 실패를 try/catch 로 삼키고 "확정은 진행한다"고 주석까지
 * 달아 두었지만, createEscrow 가 REQUIRED 로 같은 트랜잭션에 합류하므로 실패가 rollback-only 를 남긴다. 삼켜도
 * 바깥 커밋에서 터져 전부 롤백됐다 — 즉 "차감 없는 확정"은 애초에 만들어질 수 없었고, DB 상태는 수정 전후가 동일하다.
 * 상태만 단언하면 옛 코드에서도 통과하는 눈먼 테스트가 된다. 실제로 달라진 것은 호출자가 받는 예외이고(옛: 원인을 알 수 없는
 * UnexpectedRollbackException / 지금: InsufficientBalanceException = HTTP 400), 그래서 그걸 단언한다.
 */
@SpringBootTest
class CareDealEscrowFailureTest {

    private static final int OFFERED_COINS = 1_000;

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
    private CareRequest careRequest;
    private Conversation conversation;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        long uniqueId = System.currentTimeMillis();

        // 요청자 잔액은 기본값 0 — OFFERED_COINS 를 감당할 수 없다(차감 실패를 만드는 조건).
        requester = usersRepository.save(Users.builder()
                .id("escrowfail_req_" + uniqueId)
                .username("EscrowFailRequester_" + uniqueId)
                .email("escrowfail_req_" + uniqueId + "@test.com")
                .password("password123")
                .nickname("EscrowFailReq_" + uniqueId)
                .role(Role.USER)
                .build());

        provider = usersRepository.save(Users.builder()
                .id("escrowfail_prv_" + uniqueId)
                .username("EscrowFailProvider_" + uniqueId)
                .email("escrowfail_prv_" + uniqueId + "@test.com")
                .password("password123")
                .nickname("EscrowFailPrv_" + uniqueId)
                .role(Role.USER)
                .build());

        careRequest = CareRequest.builder()
                .user(requester)
                .title("Escrow Failure Test Request")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .status(CareRequestStatus.OPEN)
                .offeredCoins(OFFERED_COINS)   // 이 값이 있어야 에스크로 분기로 들어간다
                .build();
        careRequestRepository.save(careRequest);

        conversation = Conversation.builder()
                .conversationType(ConversationType.CARE_REQUEST)
                .relatedType(RelatedType.CARE_REQUEST)
                .relatedIdx(careRequest.getIdx())
                .status(ConversationStatus.ACTIVE)
                .build();
        conversationRepository.save(conversation);

        participantRepository.save(ConversationParticipant.builder()
                .conversation(conversation)
                .user(requester)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.ACTIVE)
                .dealConfirmed(false)
                .build());

        participantRepository.save(ConversationParticipant.builder()
                .conversation(conversation)
                .user(provider)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.ACTIVE)
                .dealConfirmed(false)
                .build());
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        if (conversation != null) {
            List<ConversationParticipant> participants = participantRepository
                    .findByConversationIdxAndStatus(conversation.getIdx(), ParticipantStatus.ACTIVE);
            if (!participants.isEmpty()) {
                participantRepository.deleteAllInBatch(participants);
            }
            conversationRepository.deleteById(conversation.getIdx());
        }
        if (careRequest != null) {
            careRequestRepository.deleteById(careRequest.getIdx());
        }
        if (requester != null)
            usersRepository.deleteById(requester.getIdx());
        if (provider != null)
            usersRepository.deleteById(provider.getIdx());
    }

    @Test
    @DisplayName("잔액이 모자라 차감이 실패하면, 원인 예외가 호출자까지 그대로 전파된다")
    void confirmCareDeal_whenBalanceInsufficient_propagatesCause() {
        // Given: 요청자가 먼저 확정 — 아직 한쪽이라 에스크로 분기로 가지 않는다
        conversationService.confirmCareDeal(conversation.getIdx(), requester.getIdx());

        // When: 제공자까지 확정하면 양쪽 완료 → 차감 + 에스크로 생성 시도 → 잔액 부족
        // Then: 삼켜지지 않고 원인 그대로 올라와야 한다.
        //       옛 코드에서는 catch 가 삼킨 뒤 커밋 시점에 UnexpectedRollbackException 이 나므로 여기서 실패한다.
        assertThatThrownBy(
                () -> conversationService.confirmCareDeal(conversation.getIdx(), provider.getIdx()))
                        .as("잔액 부족이 원인인데 다른 예외가 나오면 호출자는 이유를 알 수 없다 (HTTP 400 이 아니라 500 이 나간다)")
                        .isInstanceOf(InsufficientBalanceException.class);

        // 보조 단언: 확정이 롤백돼 OPEN 으로 남는다.
        // 이 단언만으로는 수정 여부를 가리지 못한다(옛 코드도 롤백됐다) — 계약을 고정하는 용도다.
        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getStatus())
                .as("차감이 실패했는데 거래만 확정되면 제공자가 무보수로 일하게 된다")
                .isEqualTo(CareRequestStatus.OPEN);
    }
}
