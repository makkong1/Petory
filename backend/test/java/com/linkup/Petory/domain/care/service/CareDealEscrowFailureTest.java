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

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
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
import com.linkup.Petory.domain.payment.exception.PetCoinEscrowNotFoundException;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 거래 확정 시 에스크로 처리가 실패하면 원인이 삼켜지지 않고 호출자까지 전파되는지 고정한다.
 *
 * 왜 상태가 아니라 예외를 단언하는가: 예전 코드는 실패를 try/catch 로 삼키고 "확정은 진행한다"고 주석까지
 * 달아 두었지만, 에스크로 처리가 REQUIRED 로 같은 트랜잭션에 합류하므로 실패가 rollback-only 를 남긴다.
 * 삼켜도 바깥 커밋에서 터져 전부 롤백됐다 — 즉 "정산 없는 확정"은 애초에 만들어질 수 없었고, DB 상태는
 * 수정 전후가 동일하다. 상태만 단언하면 옛 코드에서도 통과하는 눈먼 테스트가 된다. 실제로 달라진 것은
 * 호출자가 받는 예외다(옛: 원인을 알 수 없는 UnexpectedRollbackException).
 *
 * 재현하는 실패: 에스크로가 없는 요청의 확정.
 * 차감 시점을 등록으로 옮긴 뒤로 확정에서 잔액 부족이 날 수는 없지만, 이행 전 데이터로 만들어진
 * 요청(등록 시 에스크로를 잡지 않던 시절)은 에스크로 없이 남아 있고 그대로 두기로 했다.
 * 그 요청이 확정되면 배정할 보관이 없다 — 조용히 넘어가지 않고 드러나야 한다.
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

    @Autowired
    private CareApplicationRepository careApplicationRepository;

    private Users requester;
    private Users provider;
    private CareRequest careRequest;
    private Conversation conversation;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        long uniqueId = System.currentTimeMillis();

        // 요청을 서비스가 아니라 리포지토리로 직접 만든다 = 등록 시 에스크로를 잡지 않던 시절의 데이터.
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

        // 케어 채팅방은 지원 단위다 — relatedIdx 는 careApplicationIdx.
        // 에스크로는 일부러 만들지 않는다(이행 전 데이터 재현).
        CareApplication application = careApplicationRepository.saveAndFlush(CareApplication.builder()
                .careRequest(careRequest)
                .provider(provider)
                .status(CareApplicationStatus.PENDING)
                .build());

        conversation = Conversation.builder()
                .conversationType(ConversationType.CARE_REQUEST)
                .relatedType(RelatedType.CARE_APPLICATION)
                .relatedIdx(application.getIdx())
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
    @DisplayName("보관된 에스크로가 없으면, 원인 예외가 호출자까지 그대로 전파된다")
    void confirmCareDeal_whenEscrowMissing_propagatesCause() {
        // Given: 요청자가 먼저 확정 — 아직 한쪽이라 에스크로 분기로 가지 않는다
        conversationService.confirmCareDeal(conversation.getIdx(), requester.getIdx(), null);

        // When: 제공자까지 확정하면 양쪽 완료 → 지급 대상 배정 시도 → 배정할 보관이 없음
        // Then: 삼켜지지 않고 원인 그대로 올라와야 한다.
        //       옛 코드에서는 catch 가 삼킨 뒤 커밋 시점에 UnexpectedRollbackException 이 나므로 여기서 실패한다.
        assertThatThrownBy(
                () -> conversationService.confirmCareDeal(conversation.getIdx(), provider.getIdx(), null))
                        .as("원인이 다른 예외로 덮이면 호출자는 이유를 알 수 없다 (HTTP 500 이 나간다)")
                        .isInstanceOf(PetCoinEscrowNotFoundException.class);

        // 보조 단언: 확정이 롤백돼 OPEN 으로 남는다.
        // 이 단언만으로는 수정 여부를 가리지 못한다(옛 코드도 롤백됐다) — 계약을 고정하는 용도다.
        assertThat(careRequestRepository.findById(careRequest.getIdx()).orElseThrow().getStatus())
                .as("차감이 실패했는데 거래만 확정되면 제공자가 무보수로 일하게 된다")
                .isEqualTo(CareRequestStatus.OPEN);
    }
}
