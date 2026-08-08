package com.linkup.Petory.domain.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.exception.CareApplicationNotFoundException;
import com.linkup.Petory.domain.payment.exception.PaymentConflictException;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.chat.converter.ChatMessageConverter;
import com.linkup.Petory.domain.chat.converter.ConversationConverter;
import com.linkup.Petory.domain.chat.converter.ConversationParticipantConverter;
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.exception.ChatForbiddenException;
import com.linkup.Petory.domain.chat.exception.ChatValidationException;
import com.linkup.Petory.domain.chat.exception.ConversationNotFoundException;
import com.linkup.Petory.domain.chat.repository.ChatMessageRepository;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.payment.service.PetCoinEscrowService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsersRepository usersRepository;
    private final ConversationConverter conversationConverter;
    private final ConversationParticipantConverter participantConverter;
    private final ChatMessageConverter messageConverter;
    private final CareRequestRepository careRequestRepository;
    private final CareApplicationRepository careApplicationRepository;
    private final PetCoinEscrowService petCoinEscrowService;
    private final ConversationCreatorService conversationCreatorService;
    private final MeetupParticipantsRepository meetupParticipantsRepository;

    /**
     * 사용자별 활성 채팅방 목록 조회 (N+1 문제 최적화)
     */
    public List<ConversationDTO> getMyConversations(Long userId) {
        // 탈퇴하지 않은 사용자의 채팅방만 조회
        List<Conversation> conversations = conversationRepository
                .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);

        if (conversations.isEmpty()) {
            return new ArrayList<>();
        }

        // 채팅방 ID 목록 추출
        List<Long> conversationIdxs = conversations.stream()
                .map(Conversation::getIdx)
                .collect(Collectors.toList());

        // 배치 조회: 현재 사용자의 참여자 정보 (읽지 않은 메시지 수 포함)
        List<ConversationParticipant> myParticipants = participantRepository
                .findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
        Map<Long, ConversationParticipant> myParticipantMap = myParticipants.stream()
                .collect(Collectors.toMap(
                        p -> p.getConversation().getIdx(),
                        p -> p,
                        (existing, replacement) -> existing));

        // 배치 조회: 모든 활성 참여자 정보
        List<ConversationParticipant> allParticipants = participantRepository
                .findParticipantsByConversationIdxsAndStatus(conversationIdxs,
                        ParticipantStatus.ACTIVE);
        Map<Long, List<ConversationParticipant>> participantsMap = allParticipants.stream()
                .collect(Collectors.groupingBy(p -> p.getConversation().getIdx()));

        // 배치 조회: 각 채팅방의 최신 메시지
        List<ChatMessage> latestMessages = chatMessageRepository
                .findLatestMessagesByConversationIdxs(conversationIdxs);
        Map<Long, ChatMessage> latestMessageMap = latestMessages.stream()
                .collect(Collectors.toMap(
                        m -> m.getConversation().getIdx(),
                        m -> m,
                        (existing, replacement) -> existing));

        // DTO 변환
        return conversations.stream()
                .map(conv -> {
                    ConversationDTO dto = conversationConverter.toDTO(conv);

                    // 현재 사용자의 참여자 정보 추가 (읽지 않은 메시지 수 포함)
                    ConversationParticipant myParticipant = myParticipantMap.get(conv.getIdx());
                    if (myParticipant != null) {
                        dto.setUnreadCount(myParticipant.getUnreadCount());
                    }

                    // 참여자 정보 추가 (배치 로드 데이터 사용 — lazy load 방지)
                    List<ConversationParticipant> participants = participantsMap.getOrDefault(
                            conv.getIdx(),
                            new ArrayList<>());
                    dto.setParticipantCount(participants.size());
                    if (!participants.isEmpty()) {
                        dto.setParticipants(participantConverter.toDTOList(participants));
                        // 제재 안내 플래그: 활성 참여자 중 제재 중인 사용자 존재 시 true
                        boolean hasSanctioned = participants.stream()
                                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                                .anyMatch(p -> p.getUser().isSanctioned());
                        dto.setHasSanctionedParticipant(hasSanctioned);
                    }

                    // 마지막 메시지 추가
                    ChatMessage lastMessage = latestMessageMap.get(conv.getIdx());
                    if (lastMessage != null) {
                        dto.setLastMessage(messageConverter.toDTO(lastMessage));
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 채팅방 상세 조회
     */
    public ConversationDTO getConversation(Long conversationIdx, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationIdx)
                .orElseThrow(ConversationNotFoundException::new);

        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE
                && !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(ChatForbiddenException::notParticipant);

        List<ConversationParticipant> participants = participantRepository
                .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);

        boolean hasDeletedUser = participants.stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getUser().getIsDeleted()));
        if (hasDeletedUser) {
            throw ChatValidationException.invalidConversation();
        }

        ConversationDTO dto = conversationConverter.toDTO(conversation);
        dto.setParticipantCount(participants.size());
        dto.setParticipants(participantConverter.toDTOList(participants));
        dto.setUnreadCount(participant.getUnreadCount());
        // 제재 안내 플래그
        boolean hasSanctioned = participants.stream()
                .anyMatch(p -> p.getUser().isSanctioned());
        dto.setHasSanctionedParticipant(hasSanctioned);
        return dto;
    }

    /**
     * 채팅방 생성. 실제 생성은 {@link ConversationCreatorService}의 REQUIRES_NEW 트랜잭션에서
     * 수행한다.
     */
    @Transactional
    public ConversationDTO createConversation(
            ConversationType conversationType,
            RelatedType relatedType,
            Long relatedIdx,
            String title,
            List<Long> participantUserIds,
            Long actingUserId) {
        return conversationCreatorService.createConversation(
                conversationType,
                relatedType,
                relatedIdx,
                title,
                participantUserIds,
                actingUserId);
    }

    /**
     * 펫케어 요청 채팅방 생성 (CareApplication 승인 시)
     */
    @Transactional
    public ConversationDTO createCareRequestConversation(Long careApplicationIdx, Long currentUserId) {
        CareApplication application = careApplicationRepository.findById(careApplicationIdx)
                .orElseThrow(CareApplicationNotFoundException::new);
        Long requesterId = application.getCareRequest().getUser().getIdx();
        Long providerId = application.getProvider().getIdx();
        if (!currentUserId.equals(requesterId) && !currentUserId.equals(providerId)) {
            throw ChatForbiddenException.notCareApplicationParty();
        }

        Optional<Conversation> existing = conversationRepository
                .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(RelatedType.CARE_APPLICATION,
                        careApplicationIdx);

        if (existing.isPresent() && !Boolean.TRUE.equals(existing.get().getIsDeleted())) {
            return conversationConverter.toDTO(existing.get());
        }

        return conversationCreatorService.createConversation(
                ConversationType.CARE_REQUEST,
                RelatedType.CARE_APPLICATION,
                careApplicationIdx,
                null,
                List.of(requesterId, providerId),
                currentUserId);
    }

    /**
     * 1:1 일반 채팅방 생성 또는 조회
     */
    @Transactional
    public ConversationDTO getOrCreateDirectConversation(Long currentUserId, Long otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw ChatValidationException.cannotChatWithSelf();
        }

        Optional<Conversation> existing = conversationRepository.findDirectConversationBetweenUsers(
                currentUserId,
                otherUserId);

        if (existing.isPresent() && !Boolean.TRUE.equals(existing.get().getIsDeleted())) {
            return conversationConverter.toDTO(existing.get());
        }

        return conversationCreatorService.createConversation(
                ConversationType.DIRECT,
                null,
                null,
                null,
                List.of(currentUserId, otherUserId),
                currentUserId);
    }

    /**
     * 채팅방 나가기
     */
    @Transactional
    public void leaveConversation(Long conversationIdx, Long userId) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(ChatForbiddenException::notParticipant);

        participant.setStatus(ParticipantStatus.LEFT);
        participant.setLeftAt(LocalDateTime.now());
        participant.softDelete();
        participantRepository.save(participant);

        // 참여자가 없으면 채팅방 비활성화
        List<ConversationParticipant> activeParticipants = participantRepository
                .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);

        if (activeParticipants.isEmpty()) {
            Conversation conversation = conversationRepository.findById(conversationIdx)
                    .orElseThrow();
            conversation.close();
            conversationRepository.save(conversation);
        }
    }

    /**
     * 채팅방 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteConversation(Long conversationIdx, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationIdx)
                .orElseThrow(ConversationNotFoundException::new);

        // 참여자인지 확인
        participantRepository.findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(ChatForbiddenException::notParticipant);

        conversation.softDelete();
        conversationRepository.save(conversation);
    }

    /**
     * 채팅방 상태 변경
     */
    @Transactional
    public ConversationDTO updateConversationStatus(Long conversationIdx, ConversationStatus status,
            Long actingUserId) {
        participantRepository.findByConversationIdxAndUserIdx(conversationIdx, actingUserId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE
                && !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(ChatForbiddenException::notParticipant);

        Conversation conversation = conversationRepository.findById(conversationIdx)
                .orElseThrow(ConversationNotFoundException::new);

        conversation.setStatus(status);
        conversation = conversationRepository.save(conversation);

        return conversationConverter.toDTO(conversation);
    }

    /**
     * 산책모임 채팅방 참여
     */
    @Transactional
    public ConversationDTO joinMeetupChat(Long meetupIdx, Long userId) {
        // 모임 참여자인지 검증
        if (!meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userId)) {
            throw ChatForbiddenException.notMeetupParticipant();
        }

        // 모임의 채팅방 찾기
        Conversation conversation = conversationRepository
                .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(RelatedType.MEETUP, meetupIdx)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // 이미 참여 중인지 확인
        Optional<ConversationParticipant> existing = participantRepository
                .findByConversationIdxAndUserIdx(conversation.getIdx(), userId);

        if (existing.isPresent()) {
            ConversationParticipant participant = existing.get();
            // LEFT 상태였다면 ACTIVE로 변경 (재참여)
            if (participant.getStatus() == ParticipantStatus.LEFT) {
                participant.setStatus(ParticipantStatus.ACTIVE);
                participant.setJoinedAt(LocalDateTime.now());
                // 이전 대화 내용 못 보도록 lastReadMessageIdx 초기화
                participant.setLastReadMessage(null);
                participant.setLastReadAt(null);
                participant.setUnreadCount(0);
                participantRepository.save(participant);
            }
            return conversationConverter.toDTO(conversation);
        }

        // 새로 참여
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        ConversationParticipant participant = ConversationParticipant.builder()
                .conversation(conversation)
                .user(user)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.ACTIVE)
                .unreadCount(0)
                .lastReadMessage(null) // 새 참여자는 이전 메시지 못 봄
                .build();
        participantRepository.save(participant);

        return conversationConverter.toDTO(conversation);
    }

    /**
     * 산책모임 채팅방 나가기
     */
    @Transactional
    public void leaveMeetupChat(Long meetupIdx, Long userId) {
        // 모임의 채팅방 찾기
        Conversation conversation = conversationRepository
                .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(RelatedType.MEETUP, meetupIdx)
                .orElse(null);

        if (conversation == null) {
            return; // 채팅방이 없으면 무시
        }

        // 참여자 확인
        Optional<ConversationParticipant> participant = participantRepository
                .findByConversationIdxAndUserIdx(conversation.getIdx(), userId);

        if (participant.isPresent()) {
            ConversationParticipant p = participant.get();
            p.setStatus(ParticipantStatus.LEFT);
            p.setLeftAt(LocalDateTime.now());
            participantRepository.save(p);
        }
    }

    /**
     * 산책모임 채팅방 참여 인원 수 조회
     */
    public Integer getMeetupChatParticipantCount(Long meetupIdx) {
        Optional<Conversation> conversation = conversationRepository
                .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(RelatedType.MEETUP, meetupIdx);

        if (conversation.isEmpty()) {
            return 0;
        }

        return participantRepository
                .countByConversationIdxAndStatus(conversation.get().getIdx(), ParticipantStatus.ACTIVE);
    }

    /**
     * 채팅방 참여자 역할 설정 별도 트랜잭션으로 실행하여 실패해도 호출한 트랜잭션에 영향을 주지 않음
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setParticipantRole(RelatedType relatedType, Long relatedIdx, Long userId, ParticipantRole role) {
        Optional<Conversation> conversation = conversationRepository
                .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(relatedType, relatedIdx);

        if (conversation.isEmpty()) {
            return;
        }

        Optional<ConversationParticipant> participant = participantRepository
                .findByConversationIdxAndUserIdx(conversation.get().getIdx(), userId);

        if (participant.isPresent()) {
            participant.get().setRole(role);
            participantRepository.save(participant.get());
        }
    }

    /**
     * 실종제보 채팅방 생성 또는 조회 같은 제보에 대해 여러 목격자가 있을 수 있으므로, 제보자-목격자 조합별로 개별 채팅방 생성
     */
    @Transactional
    public ConversationDTO createMissingPetChat(Long boardIdx, Long reporterId, Long witnessId) {
        // 목격자가 제보자와 같은 경우 체크
        if (reporterId.equals(witnessId)) {
            throw ChatValidationException.ownReportCannotChat();
        }

        List<Conversation> conversations = conversationRepository
                .findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
                        RelatedType.MISSING_PET_BOARD,
                        List.of(boardIdx));

        if (conversations.isEmpty()) {
            return conversationCreatorService.createConversation(
                    ConversationType.MISSING_PET,
                    RelatedType.MISSING_PET_BOARD,
                    boardIdx,
                    null,
                    List.of(reporterId, witnessId),
                    witnessId);
        }

        List<Long> conversationIdxs = conversations.stream()
                .map(Conversation::getIdx)
                .collect(Collectors.toList());
        List<ConversationParticipant> allActive = participantRepository
                .findParticipantsByConversationIdxsAndStatus(conversationIdxs,
                        ParticipantStatus.ACTIVE);
        Map<Long, Set<Long>> participantIdsByConversation = allActive.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getConversation().getIdx(),
                        Collectors.mapping(p -> p.getUser().getIdx(), Collectors.toSet())));

        Optional<Conversation> existing = conversations.stream()
                .filter(conv -> {
                    Set<Long> ids = participantIdsByConversation.getOrDefault(conv.getIdx(),
                            Set.of());
                    return ids.contains(reporterId) && ids.contains(witnessId);
                })
                .findFirst();

        if (existing.isPresent()) {
            return conversationConverter.toDTO(existing.get());
        }

        return conversationCreatorService.createConversation(
                ConversationType.MISSING_PET,
                RelatedType.MISSING_PET_BOARD,
                boardIdx,
                null,
                List.of(reporterId, witnessId),
                witnessId);
    }

    /**
     * 펫케어 거래 확정 (양쪽 모두 확인 시 지원 승인 및 상태 변경)
     */
    @Transactional
    public void confirmCareDeal(Long conversationIdx, Long userId, Integer expectedAmount) {
        // 비관적 락으로 채팅방 조회 (동시성 제어)
        Conversation conversation = conversationRepository.findByIdWithLock(conversationIdx)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // 케어 채팅방은 지원(CareApplication) 단위로 만들어진다.
        // 한 요청(CareRequest)에는 제공자가 여러 명 지원할 수 있어, 요청 단위로 방을 만들면
        // 지원자 전원이 한 방에 들어가게 된다. 그래서 1:1 방은 지원 단위이고 relatedIdx 는
        // careApplicationIdx 다(createCareRequestConversation 참고).
        //
        // 이전에는 이 메서드가 RelatedType.CARE_REQUEST 를 전제로 쓰여 있었다. 방에 있는 참여자로
        // 제공자를 역추론하고 지원이 없으면 새로 만드는 로직이었는데, 실제로 생성되는 방은 전부
        // CARE_APPLICATION 이라 그 분기는 한 번도 실행되지 않았다 — 확정 버튼을 눌러도 참여자
        // 플래그만 켜지고 요청 상태·지원 상태·에스크로는 그대로였다. 지원 단위로 정리하면
        // 역추론이 통째로 없어진다.
        if (conversation.getRelatedType() != RelatedType.CARE_APPLICATION
                || conversation.getRelatedIdx() == null) {
            throw new IllegalArgumentException("펫케어 지원 채팅방이 아닙니다.");
        }

        CareApplication application = careApplicationRepository.findById(conversation.getRelatedIdx())
                .orElseThrow(CareApplicationNotFoundException::new);
        CareRequest careRequest = application.getCareRequest();

        // 사용자의 참여자 정보 조회
        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        // 제재 사용자 거래 확정 차단
        if (participant.getUser().isSanctioned()) {
            throw ChatForbiddenException.sanctionedPartyCannotConfirmDeal();
        }

        List<ConversationParticipant> allParticipants = participantRepository
                .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);
        if (allParticipants.stream().anyMatch(p -> p.getUser().isSanctioned())) {
            throw ChatForbiddenException.sanctionedPartyCannotConfirmDeal();
        }

        // 금액 대조 + 낡은 확정 무효화.
        // 확정은 양쪽이 따로 누르므로, 한쪽이 5,000 에 동의한 뒤 금액이 1,000 으로 바뀌고 다른 쪽이
        // 1,000 에 동의하면 서로 다른 금액에 동의한 채 계약이 성립한다. 두 가지가 각각 다른 걸 막는다.
        //   - expectedAmount        : 지금 내가 화면에서 보고 동의하는 값이 실제와 같은가
        //   - confirmedOfferedCoins : 이미 있는 동의가 현재 금액과 같은 금액에 대한 것인가
        // 지키려는 불변식은 "성립한 계약의 모든 동의는 같은 금액에 대한 것"이다.
        // 낡은 동의는 무효화하고 새 금액으로 다시 받는다. care 의 확정 플래그를 care 쪽에서 건드리면
        // 도메인 참조가 순환하므로(지금은 chat -> care 단방향), 무효화는 여기(chat)서 한다.
        Integer currentOfferedCoins = careRequest.getOfferedCoins();

        if (expectedAmount != null && !expectedAmount.equals(currentOfferedCoins)) {
            throw PaymentConflictException.escrowAmountChanged(currentOfferedCoins);
        }

        for (ConversationParticipant p : allParticipants) {
            if (Boolean.TRUE.equals(p.getDealConfirmed())
                    && !java.util.Objects.equals(p.getConfirmedOfferedCoins(), currentOfferedCoins)) {
                log.info("금액 변경으로 거래 확정 무효화: conversationIdx={}, userId={}, 동의금액={}, 현재금액={}",
                        conversationIdx, p.getUser().getIdx(), p.getConfirmedOfferedCoins(),
                        currentOfferedCoins);
                p.setDealConfirmed(false);
                p.setDealConfirmedAt(null);
                p.setConfirmedOfferedCoins(null);
                participantRepository.save(p);
            }
        }

        // 이미 거래 확정했는지 확인
        if (Boolean.TRUE.equals(participant.getDealConfirmed())) {
            throw new IllegalStateException("이미 거래 확정을 완료했습니다.");
        }

        // 거래 확정 처리
        participant.setDealConfirmed(true);
        participant.setDealConfirmedAt(LocalDateTime.now());
        participant.setConfirmedOfferedCoins(currentOfferedCoins);
        participantRepository.save(participant);

        // 양쪽 모두 거래 확정했는지 확인
        boolean allConfirmed = allParticipants.stream()
                .allMatch(p -> Boolean.TRUE.equals(p.getDealConfirmed()));

        // 양쪽 모두 확정했으면 지원 승인 + 요청 상태 전이 + 에스크로 지급 대상 배정
        if (allConfirmed && allParticipants.size() == 2) {
            // 다른 방에서 이미 확정된 요청이면 여기서 멈춘다. 조용히 넘어가면 사용자는
            // 확정이 된 줄 알고 기다리게 된다 — 이유를 알려준다.
            if (careRequest.getStatus() != CareRequestStatus.OPEN) {
                throw new IllegalStateException(
                        "이미 다른 제공자와 거래가 확정된 요청입니다. 현재 상태: " + careRequest.getStatus());
            }

            Users requester = careRequest.getUser();
            Users provider = application.getProvider();
            if (requester.isSanctioned() || provider.isSanctioned()) {
                throw ChatForbiddenException.sanctionedPartyCannotConfirmDeal();
            }

            application.accept();

            // 같은 요청의 나머지 지원은 선정되지 않았다. PENDING 으로 두면 그 지원자들은
            // 계속 대기 중인 줄 알게 된다.
            if (careRequest.getApplications() != null) {
                for (CareApplication other : careRequest.getApplications()) {
                    if (!other.getIdx().equals(application.getIdx())
                            && other.getStatus() == CareApplicationStatus.PENDING) {
                        other.reject();
                        careApplicationRepository.saveAndFlush(other);
                    }
                }
            }

            careRequest.transitionTo(CareRequestStatus.IN_PROGRESS);
            careRequestRepository.save(careRequest);

            // 코인은 요청 등록 시 이미 에스크로에 잡혀 있다. 확정에서 하는 일은 지급 대상을
            // 배정하는 것뿐이고, 여기서 잔액이 모자라 깨지는 일은 없다.
            // (그 TOCTOU 를 없애려고 차감을 등록 시점으로 옮겼다.)
            // 실패해도 예외는 그대로 전파한다 — 배정 없이 확정만 남으면 지급 대상이 사라진다.
            // 고정: CareDealEscrowFailureTest
            petCoinEscrowService.assignProvider(careRequest, provider, application, expectedAmount);

            log.info("거래 확정 완료: conversationIdx={}, careApplicationIdx={}, careRequestIdx={}, providerId={}, amount={}",
                    conversationIdx, application.getIdx(), careRequest.getIdx(),
                    provider.getIdx(), currentOfferedCoins);
        }
    }
}
