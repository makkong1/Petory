package com.linkup.Petory.domain.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.chat.converter.ConversationConverter;
import com.linkup.Petory.domain.chat.converter.ConversationParticipantConverter;
import com.linkup.Petory.domain.chat.converter.ChatMessageConverter;
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UsersRepository usersRepository;
    private final ConversationConverter conversationConverter;
    private final ConversationParticipantConverter participantConverter;
    private final ChatMessageConverter messageConverter;

    /**
     * 사용자별 활성 채팅방 목록 조회
     */
    public List<ConversationDTO> getMyConversations(Long userId) {
        // 탈퇴하지 않은 사용자의 채팅방만 조회
        List<Conversation> conversations = conversationRepository
                .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);

        return conversations.stream()
                .map(conv -> {
                    ConversationDTO dto = conversationConverter.toDTO(conv);
                    
                    // 현재 사용자의 참여자 정보 추가 (읽지 않은 메시지 수 포함)
                    ConversationParticipant myParticipant = participantRepository
                            .findByConversationIdxAndUserIdx(conv.getIdx(), userId)
                            .orElse(null);
                    
                    if (myParticipant != null) {
                        dto.setUnreadCount(myParticipant.getUnreadCount());
                    }
                    
                    // 참여자 정보 추가
                    List<ConversationParticipant> participants = participantRepository
                            .findByConversationIdxAndStatus(conv.getIdx(), ParticipantStatus.ACTIVE);
                    if (participants != null) {
                        dto.setParticipants(participantConverter.toDTOList(participants));
                    }
                    
                    // 마지막 메시지 추가
                    if (conv.getMessages() != null && !conv.getMessages().isEmpty()) {
                        conv.getMessages().stream()
                                .max((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                                .ifPresent(lastMessage -> {
                                    dto.setLastMessage(messageConverter.toDTO(lastMessage));
                                });
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
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // 참여자인지 확인
        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        // 탈퇴한 사용자가 포함된 채팅방인지 확인
        List<ConversationParticipant> participants = participantRepository
                .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);

        boolean hasDeletedUser = participants.stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getUser().getIsDeleted()));

        if (hasDeletedUser) {
            throw new IllegalArgumentException("유효하지 않은 채팅방입니다.");
        }

        ConversationDTO dto = conversationConverter.toDTO(conversation);
        dto.setParticipants(participantConverter.toDTOList(participants));
        dto.setUnreadCount(participant.getUnreadCount());

        return dto;
    }

    /**
     * 채팅방 생성
     */
    @Transactional
    public ConversationDTO createConversation(
            ConversationType conversationType,
            RelatedType relatedType,
            Long relatedIdx,
            String title,
            List<Long> participantUserIds) {

        // 참여자 유효성 검증
        List<Users> participants = participantUserIds.stream()
                .map(userId -> usersRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId)))
                .collect(Collectors.toList());

        // 탈퇴한 사용자 제외
        participants = participants.stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .collect(Collectors.toList());

        if (participants.size() < 2) {
            throw new IllegalArgumentException("최소 2명의 참여자가 필요합니다.");
        }

        // 1:1 채팅인 경우 기존 채팅방 확인
        if (conversationType == ConversationType.DIRECT && participants.size() == 2) {
            Optional<Conversation> existing = conversationRepository.findDirectConversationBetweenUsers(
                    participants.get(0).getIdx(),
                    participants.get(1).getIdx());

            if (existing.isPresent()) {
                return conversationConverter.toDTO(existing.get());
            }
        }

        // Conversation 생성
        Conversation conversation = Conversation.builder()
                .conversationType(conversationType)
                .relatedType(relatedType)
                .relatedIdx(relatedIdx)
                .title(title)
                .status(ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);

        // 참여자 추가
        for (Users user : participants) {
            ConversationParticipant participant = ConversationParticipant.builder()
                    .conversation(conversation)
                    .user(user)
                    .role(ParticipantRole.MEMBER)
                    .status(ParticipantStatus.ACTIVE)
                    .unreadCount(0)
                    .build();
            participantRepository.save(participant);
        }

        return conversationConverter.toDTO(conversation);
    }

    /**
     * 펫케어 요청 채팅방 생성 (CareApplication 승인 시)
     */
    @Transactional
    public ConversationDTO createCareRequestConversation(Long careApplicationIdx, Long requesterId, Long providerId) {
        // 이미 채팅방이 있는지 확인
        Optional<Conversation> existing = conversationRepository
                .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(RelatedType.CARE_APPLICATION, careApplicationIdx);

        if (existing.isPresent() && !Boolean.TRUE.equals(existing.get().getIsDeleted())) {
            return conversationConverter.toDTO(existing.get());
        }

        return createConversation(
                ConversationType.CARE_REQUEST,
                RelatedType.CARE_APPLICATION,
                careApplicationIdx,
                null,
                List.of(requesterId, providerId));
    }

    /**
     * 1:1 일반 채팅방 생성 또는 조회
     */
    @Transactional
    public ConversationDTO getOrCreateDirectConversation(Long user1Id, Long user2Id) {
        // 기존 채팅방 확인
        Optional<Conversation> existing = conversationRepository.findDirectConversationBetweenUsers(user1Id, user2Id);

        if (existing.isPresent() && !Boolean.TRUE.equals(existing.get().getIsDeleted())) {
            return conversationConverter.toDTO(existing.get());
        }

        // 새로 생성
        return createConversation(
                ConversationType.DIRECT,
                null,
                null,
                null,
                List.of(user1Id, user2Id));
    }

    /**
     * 채팅방 나가기
     */
    @Transactional
    public void leaveConversation(Long conversationIdx, Long userId) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        participant.setStatus(ParticipantStatus.LEFT);
        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);

        // 참여자가 없으면 채팅방 비활성화
        List<ConversationParticipant> activeParticipants = participantRepository
                .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);

        if (activeParticipants.isEmpty()) {
            Conversation conversation = conversationRepository.findById(conversationIdx)
                    .orElseThrow();
            conversation.setStatus(ConversationStatus.CLOSED);
            conversationRepository.save(conversation);
        }
    }

    /**
     * 채팅방 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteConversation(Long conversationIdx, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationIdx)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // 참여자인지 확인
        participantRepository.findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        conversation.setIsDeleted(true);
        conversation.setDeletedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * 채팅방 상태 변경
     */
    @Transactional
    public ConversationDTO updateConversationStatus(Long conversationIdx, ConversationStatus status) {
        Conversation conversation = conversationRepository.findById(conversationIdx)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        conversation.setStatus(status);
        conversation = conversationRepository.save(conversation);

        return conversationConverter.toDTO(conversation);
    }

    /**
     * 산책모임 채팅방 참여
     */
    @Transactional
    public ConversationDTO joinMeetupChat(Long meetupIdx, Long userId) {
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
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

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
     * 채팅방 참여자 역할 설정
     */
    @Transactional
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
     * 실종제보 채팅방 생성 또는 조회
     * 같은 제보에 대해 여러 목격자가 있을 수 있으므로,
     * 제보자-목격자 조합별로 개별 채팅방 생성
     */
    @Transactional
    public ConversationDTO createMissingPetChat(Long boardIdx, Long reporterId, Long witnessId) {
        // 목격자가 제보자와 같은 경우 체크
        if (reporterId.equals(witnessId)) {
            throw new IllegalArgumentException("본인의 제보에는 채팅을 시작할 수 없습니다.");
        }

        // 같은 제보(boardIdx)에 대한 모든 채팅방 조회
        List<Conversation> conversations = conversationRepository
                .findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
                    RelatedType.MISSING_PET_BOARD, 
                    List.of(boardIdx));

        // 제보자와 목격자가 모두 참여한 채팅방 찾기
        Optional<Conversation> existing = conversations.stream()
                .filter(conv -> {
                    List<ConversationParticipant> participants = participantRepository
                            .findByConversationIdxAndStatus(conv.getIdx(), ParticipantStatus.ACTIVE);
                    
                    java.util.Set<Long> participantIds = participants.stream()
                            .map(p -> p.getUser().getIdx())
                            .collect(Collectors.toSet());
                    
                    return participantIds.contains(reporterId) && participantIds.contains(witnessId);
                })
                .findFirst();

        if (existing.isPresent()) {
            // 기존 채팅방 반환
            return conversationConverter.toDTO(existing.get());
        }

        // 새 채팅방 생성
        return createConversation(
                ConversationType.MISSING_PET,
                RelatedType.MISSING_PET_BOARD,
                boardIdx,
                null,  // 1:1이므로 제목 없음
                List.of(reporterId, witnessId)
        );
    }
}

