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

}
