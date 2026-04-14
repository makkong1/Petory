package com.linkup.Petory.domain.chat.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.chat.converter.ConversationConverter;
import com.linkup.Petory.domain.chat.converter.ConversationParticipantConverter;
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.exception.ChatForbiddenException;
import com.linkup.Petory.domain.chat.exception.ChatValidationException;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 채팅방 생성 전용. {@code REQUIRES_NEW} 트랜잭션이 프록시를 타도록 동일 클래스 내부 호출과 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCreatorService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UsersRepository usersRepository;
    private final ConversationConverter conversationConverter;
    private final ConversationParticipantConverter participantConverter;

    /**
     * 채팅방 생성. 호출부 트랜잭션과 분리(REQUIRES_NEW).
     *
     * @param actingUserId 요청을 수행하는 사용자(토큰 기준). 반드시 {@code participantUserIds}에 포함되어야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationDTO createConversation(
            ConversationType conversationType,
            RelatedType relatedType,
            Long relatedIdx,
            String title,
            List<Long> participantUserIds,
            Long actingUserId) {

        List<Users> participants = participantUserIds.stream()
                .map(userId -> usersRepository.findById(userId)
                        .orElseThrow(UserNotFoundException::new))
                .collect(Collectors.toList());

        if (!participants.stream().map(Users::getIdx).anyMatch(actingUserId::equals)) {
            throw ChatForbiddenException.notAllowedToCreateConversation();
        }

        participants = participants.stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .collect(Collectors.toList());

        if (conversationType == ConversationType.MEETUP) {
            if (participants.size() < 1) {
                throw ChatValidationException.minParticipantsRequired(1);
            }
        } else {
            if (participants.size() < 2) {
                throw ChatValidationException.minParticipantsRequired(2);
            }
        }

        if (relatedType != null && relatedIdx != null) {
            Optional<Conversation> existing = conversationRepository
                    .findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(relatedType, relatedIdx);

            if (existing.isPresent()) {
                Conversation existingConv = existing.get();
                List<ConversationParticipant> existingParticipants = participantRepository
                        .findByConversationIdxAndStatus(existingConv.getIdx(), ParticipantStatus.ACTIVE);
                Set<Long> existingParticipantIds = existingParticipants.stream()
                        .map(p -> p.getUser().getIdx())
                        .collect(Collectors.toSet());
                Set<Long> newParticipantIds = participants.stream()
                        .map(Users::getIdx)
                        .collect(Collectors.toSet());

                if (existingParticipantIds.equals(newParticipantIds)) {
                    if (existingConv.getRelatedType() == null || existingConv.getRelatedIdx() == null) {
                        existingConv.setRelatedType(relatedType);
                        existingConv.setRelatedIdx(relatedIdx);
                        existingConv = conversationRepository.save(existingConv);
                    }
                    ConversationDTO dto = conversationConverter.toDTO(existingConv);
                    dto.setParticipants(participantConverter.toDTOList(existingParticipants));
                    return dto;
                }
            }
        }

        if (conversationType == ConversationType.DIRECT && participants.size() == 2) {
            Optional<Conversation> existing = conversationRepository.findDirectConversationBetweenUsers(
                    participants.get(0).getIdx(),
                    participants.get(1).getIdx());

            if (existing.isPresent()) {
                Conversation existingConv = existing.get();

                if (relatedType != null && relatedIdx != null) {
                    if (existingConv.getRelatedType() == null || existingConv.getRelatedIdx() == null) {
                        existingConv.setRelatedType(relatedType);
                        existingConv.setRelatedIdx(relatedIdx);
                        existingConv = conversationRepository.save(existingConv);
                        log.info("기존 채팅방에 relatedType 업데이트: conversationIdx={}, relatedType={}, relatedIdx={}",
                                existingConv.getIdx(), relatedType, relatedIdx);
                    }
                }

                List<ConversationParticipant> existingParticipants = participantRepository
                        .findByConversationIdxAndStatus(existingConv.getIdx(), ParticipantStatus.ACTIVE);
                ConversationDTO dto = conversationConverter.toDTO(existingConv);
                dto.setParticipants(participantConverter.toDTOList(existingParticipants));
                return dto;
            }
        }

        Conversation conversation = Conversation.builder()
                .conversationType(conversationType)
                .relatedType(relatedType)
                .relatedIdx(relatedIdx)
                .title(title)
                .status(ConversationStatus.ACTIVE)
                .build();

        conversation = conversationRepository.save(conversation);

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
}
