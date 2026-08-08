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
import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
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
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 케어 채팅방이 "지원 단위"라는 전제를 고정한다.
 *
 * 한 요청(CareRequest)에는 제공자가 여러 명 지원할 수 있으므로 1:1 채팅방은 지원(CareApplication)
 * 단위여야 한다. 실제로 `createCareRequestConversation`이 만드는 방은 전부
 * `RelatedType.CARE_APPLICATION` 이고 `relatedIdx` 는 careApplicationIdx 다.
 *
 * 그런데 `confirmCareDeal` 의 확정 로직은 `CARE_REQUEST`(= 요청 단위 방)를 전제로 쓰여 있었다.
 * 방 참여자에서 제공자를 역추론하고 지원이 없으면 새로 만드는 구조였는데, 그 분기는 실제로 생성되는
 * 방의 타입과 달라 **한 번도 실행되지 않았다.** 확정 버튼을 눌러도 참여자 플래그만 켜지고
 * 요청 상태·지원 상태·에스크로는 그대로였다 — 화면만 진행되고 도메인은 멈춰 있었다.
 *
 * 아래 테스트들이 그 경로가 실제로 도는지, 그리고 선정되지 않은 지원이 어떻게 되는지를 잠근다.
 */
@SpringBootTest
class CareDealApplicationScopeTest {

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
    private CareApplicationRepository careApplicationRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private PetCoinEscrowRepository escrowRepository;

    private Users requester;
    private final List<Users> providers = new ArrayList<>();
    private final List<CareApplication> applications = new ArrayList<>();
    private final List<Conversation> conversations = new ArrayList<>();
    private Long careRequestIdx;

    @BeforeEach
    void setup() {
        long uniqueId = System.currentTimeMillis();

        requester = usersRepository.save(Users.builder()
                .id("scope_req_" + uniqueId).username("ScopeReq_" + uniqueId)
                .email("scope_req_" + uniqueId + "@test.com").password("password123")
                .nickname("ScopeReq_" + uniqueId).role(Role.USER)
                .emailVerified(true).petCoinBalance(INITIAL_BALANCE).build());

        careRequestIdx = careRequestService.createCareRequest(CareRequestDTO.builder()
                .userId(requester.getIdx())
                .title("Application Scope Test")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .offeredCoins(OFFERED)
                .latitude(37.5).longitude(127.0).address("서울시 어딘가")
                .build()).getIdx();

        // 제공자 2명이 같은 요청에 지원하고, 각자 요청자와 1:1 방을 갖는다.
        for (int i = 0; i < 2; i++) {
            Users provider = usersRepository.save(Users.builder()
                    .id("scope_prv" + i + "_" + uniqueId).username("ScopePrv" + i + "_" + uniqueId)
                    .email("scope_prv" + i + "_" + uniqueId + "@test.com").password("password123")
                    .nickname("ScopePrv" + i + "_" + uniqueId).role(Role.USER).build());
            providers.add(provider);

            CareApplication application = careApplicationRepository.saveAndFlush(CareApplication.builder()
                    .careRequest(careRequestRepository.findById(careRequestIdx).orElseThrow())
                    .provider(provider)
                    .status(CareApplicationStatus.PENDING)
                    .build());
            applications.add(application);

            Conversation conversation = conversationRepository.save(Conversation.builder()
                    .conversationType(ConversationType.CARE_REQUEST)
                    .relatedType(RelatedType.CARE_APPLICATION)
                    .relatedIdx(application.getIdx())
                    .status(ConversationStatus.ACTIVE)
                    .build());
            conversations.add(conversation);

            for (Users u : new Users[] { requester, provider }) {
                participantRepository.save(ConversationParticipant.builder()
                        .conversation(conversation).user(u)
                        .role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE)
                        .dealConfirmed(false).build());
            }
        }
    }

    @AfterEach
    void tearDown() {
        for (Conversation c : conversations) {
            List<ConversationParticipant> participants = participantRepository
                    .findByConversationIdxAndStatus(c.getIdx(), ParticipantStatus.ACTIVE);
            if (!participants.isEmpty()) {
                participantRepository.deleteAllInBatch(participants);
            }
            conversationRepository.deleteById(c.getIdx());
        }
        if (careRequestIdx != null) {
            careRequestRepository.deleteById(careRequestIdx);
        }
        for (Users p : providers) {
            usersRepository.deleteById(p.getIdx());
        }
        if (requester != null) {
            usersRepository.deleteById(requester.getIdx());
        }
        conversations.clear();
        applications.clear();
        providers.clear();
    }

    /** 지정한 방에서 양쪽이 확정한다. */
    private void bothConfirm(int roomIndex) {
        Long conversationIdx = conversations.get(roomIndex).getIdx();
        conversationService.confirmCareDeal(conversationIdx, providers.get(roomIndex).getIdx(), OFFERED);
        conversationService.confirmCareDeal(conversationIdx, requester.getIdx(), OFFERED);
    }

    private CareApplicationStatus statusOf(int index) {
        return careApplicationRepository.findById(applications.get(index).getIdx())
                .orElseThrow().getStatus();
    }

    @Test
    @DisplayName("양쪽이 확정하면 지원이 승인되고 요청이 진행 중으로 넘어가며 지급 대상이 배정된다")
    void 확정하면_도메인이_실제로_움직인다() {
        bothConfirm(0);

        assertThat(statusOf(0))
                .as("확정했는데 지원이 PENDING 그대로면 아무 일도 일어나지 않은 것이다")
                .isEqualTo(CareApplicationStatus.ACCEPTED);

        var careRequest = careRequestRepository.findById(careRequestIdx).orElseThrow();
        assertThat(careRequest.getStatus()).isEqualTo(CareRequestStatus.IN_PROGRESS);

        var escrow = escrowRepository.findByCareRequest(careRequest).orElseThrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.HOLD);
        assertThat(escrow.isUnassigned())
                .as("지급 대상이 배정되지 않으면 나중에 정산할 상대가 없다")
                .isFalse();
        assertThat(escrow.getProvider().getIdx()).isEqualTo(providers.get(0).getIdx());
    }

    @Test
    @DisplayName("한 지원이 확정되면 나머지 지원은 선정되지 않은 것으로 정리된다")
    void 확정되면_나머지_지원은_REJECTED() {
        bothConfirm(0);

        assertThat(statusOf(1))
                .as("PENDING 으로 두면 그 지원자는 계속 대기 중인 줄 알게 된다")
                .isEqualTo(CareApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("이미 확정된 요청의 다른 방에서 확정하면 이유를 알려주고 거절한다")
    void 이미_확정된_요청은_다른_방에서_확정_불가() {
        bothConfirm(0);

        Long otherRoom = conversations.get(1).getIdx();
        conversationService.confirmCareDeal(otherRoom, providers.get(1).getIdx(), OFFERED);

        assertThatThrownBy(
                () -> conversationService.confirmCareDeal(otherRoom, requester.getIdx(), OFFERED))
                        .as("조용히 넘어가면 사용자는 확정된 줄 알고 기다린다")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("이미 다른 제공자와");

        assertThat(statusOf(0)).isEqualTo(CareApplicationStatus.ACCEPTED);
        assertThat(escrowRepository
                .findByCareRequest(careRequestRepository.findById(careRequestIdx).orElseThrow())
                .orElseThrow().getProvider().getIdx())
                        .as("지급 대상이 나중 확정으로 덮이면 안 된다")
                        .isEqualTo(providers.get(0).getIdx());
    }
}
