package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
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
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@SpringBootTest
class CareDealConcurrencyTest {

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

    @Autowired
    private PetCoinEscrowRepository escrowRepository;

    private Users requester;
    private Users provider;
    private CareRequest careRequest;
    private Conversation conversation;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        // 매번 고유한 ID 생성하여 중복 에러 완전 방지
        long uniqueId = System.currentTimeMillis();

        // 1. 사용자 생성
        requester = usersRepository.save(Users.builder()
                .id("requester_" + uniqueId)
                .username("RequesterUser_" + uniqueId)
                .email("requester_" + uniqueId + "@test.com") // 이메일도 고유하게
                .password("password123")
                .nickname("Requester_" + uniqueId) // 닉네임도 고유하게 (Unique Constraint 해결)
                .role(Role.USER)
                .build());

        provider = usersRepository.save(Users.builder()
                .id("provider_" + uniqueId)
                .username("ProviderUser_" + uniqueId)
                .email("provider_" + uniqueId + "@test.com") // 이메일도 고유하게
                .password("password123")
                .nickname("Provider_" + uniqueId) // 닉네임도 고유하게
                .role(Role.USER)
                .build());

        // 2. 케어 요청 생성
        careRequest = CareRequest.builder()
                .user(requester)
                .title("Concurrency Test Request")
                .description("Test Content")
                .date(LocalDateTime.now().plusDays(1))
                .status(CareRequestStatus.OPEN)
                .offeredCoins(1_000)
                .build();
        careRequestRepository.save(careRequest);

        // 케어 채팅방은 지원 단위로 만들어진다 — relatedIdx 는 careApplicationIdx 다.
        CareApplication application = careApplicationRepository.saveAndFlush(CareApplication.builder()
                .careRequest(careRequest)
                .provider(provider)
                .status(CareApplicationStatus.PENDING)
                .build());

        // 코인은 요청 등록 시 잡힌다. 여기서는 리포지토리로 직접 만들므로 보관도 직접 만들어 준다.
        escrowRepository.save(PetCoinEscrow.builder()
                .careRequest(careRequest)
                .requester(requester)
                .amount(1_000)
                .status(EscrowStatus.HOLD)
                .build());

        // 3. 채팅방 생성
        conversation = Conversation.builder()
                .conversationType(ConversationType.CARE_REQUEST)
                .relatedType(RelatedType.CARE_APPLICATION)
                .relatedIdx(application.getIdx())
                .status(ConversationStatus.ACTIVE)
                .build();
        conversationRepository.save(conversation);

        // 4. 참여자 추가
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
        // 이 테스트에서 생성한 데이터만 명시적으로 삭제 (순서 중요)

        // 1. 참여자 & 채팅방 삭제
        if (conversation != null) {
            // 관련된 참여자 먼저 삭제
            List<ConversationParticipant> participants = participantRepository
                    .findByConversationIdxAndStatus(conversation.getIdx(), ParticipantStatus.ACTIVE);
            if (!participants.isEmpty()) {
                participantRepository.deleteAllInBatch(participants);
            }

            conversationRepository.deleteById(conversation.getIdx());
        }

        // 2. 케어 요청 삭제 (ID로 삭제하여 영속성 문제 회피)
        if (careRequest != null) {
            // 자식 엔티티(CareApplication)가 있을 수 있으므로 먼저 삭제 시도하거나 Cascade에 의존
            // 하지만 TransientObjectException 방지를 위해 ID로 삭제
            careRequestRepository.deleteById(careRequest.getIdx());
        }

        // 3. 사용자 삭제
        if (requester != null)
            usersRepository.deleteById(requester.getIdx());
        if (provider != null)
            usersRepository.deleteById(provider.getIdx());
    }

    @Test
    @DisplayName("동시 거래 확정 시도 시 Stuck State 없이 정상적으로 상태가 변경되어야 한다")
    @SuppressWarnings("CallToPrintStackTrace")
    void confirmCareDeal_Concurrency() throws InterruptedException {
        // Given
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When
        // Requester의 확정 시도
        executorService.submit(() -> {
            try {
                conversationService.confirmCareDeal(conversation.getIdx(), requester.getIdx(), null);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        // Provider의 확정 시도
        executorService.submit(() -> {
            try {
                conversationService.confirmCareDeal(conversation.getIdx(), provider.getIdx(), null);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        latch.await();

        // Then
        // 1. 두 참여자 모두 거래 확정 상태여야 함
        List<ConversationParticipant> participants = participantRepository
                .findByConversationIdxAndStatus(conversation.getIdx(), ParticipantStatus.ACTIVE);
        boolean allConfirmed = participants.stream().allMatch(p -> Boolean.TRUE.equals(p.getDealConfirmed()));
        assertThat(allConfirmed).isTrue();

        // 2. CareRequest 상태가 IN_PROGRESS로 변경되어야 함
        // (Race Condition 발생 시 여기서 OPEN 상태로 남아있어 테스트 실패함)
        CareRequest updatedRequest = careRequestRepository.findById(careRequest.getIdx()).orElseThrow();
        assertThat(updatedRequest.getStatus())
                .as("두 사용자가 동시에 확정했을 때, Race Condition에 의해 로직이 스킵되지 않고 IN_PROGRESS 상태가 되어야 함")
                .isEqualTo(CareRequestStatus.IN_PROGRESS);
    }
}
