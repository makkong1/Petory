package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * ConversationParticipantRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaConversationParticipantAdapter implements ConversationParticipantRepository {

    private final SpringDataJpaConversationParticipantRepository jpaRepository;

    @Override
    public ConversationParticipant save(ConversationParticipant participant) {
        return jpaRepository.save(participant);
    }

    @Override
    public Optional<ConversationParticipant> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(ConversationParticipant participant) {
        jpaRepository.delete(participant);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<ConversationParticipant> findByConversationAndStatus(
            Conversation conversation,
            ParticipantStatus status) {
        return jpaRepository.findByConversationAndStatus(conversation, status);
    }

    @Override
    public List<ConversationParticipant> findByConversationIdxAndStatus(
            Long conversationIdx,
            ParticipantStatus status) {
        return jpaRepository.findByConversationIdxAndStatus(conversationIdx, status);
    }

    @Override
    public List<ConversationParticipant> findActiveParticipationsByUser(Long userId) {
        return jpaRepository.findActiveParticipationsByUser(userId);
    }

    @Override
    public Optional<ConversationParticipant> findByConversationAndUser(
            Conversation conversation,
            Users user) {
        return jpaRepository.findByConversationAndUser(conversation, user);
    }

    @Override
    public Optional<ConversationParticipant> findByConversationIdxAndUserIdx(
            Long conversationIdx,
            Long userId) {
        return jpaRepository.findByConversationIdxAndUserIdx(conversationIdx, userId);
    }

    @Override
    public List<ConversationParticipant> findUnreadConversationsByUser(Long userId) {
        return jpaRepository.findUnreadConversationsByUser(userId);
    }

    @Override
    public void markAsRead(Long conversationIdx, Long userId) {
        jpaRepository.markAsRead(conversationIdx, userId);
    }

    @Override
    public void incrementUnreadCount(Long conversationIdx, Long senderUserId) {
        jpaRepository.incrementUnreadCount(conversationIdx, senderUserId);
    }

    @Override
    public List<Object[]> countActiveParticipantsByConversationIdxs(List<Long> conversationIdxs) {
        return jpaRepository.countActiveParticipantsByConversationIdxs(conversationIdxs);
    }

    @Override
    public Integer countByConversationIdxAndStatus(
            Long conversationIdx,
            ParticipantStatus status) {
        return jpaRepository.countByConversationIdxAndStatus(conversationIdx, status);
    }

    @Override
    public List<ConversationParticipant> findParticipantsByConversationIdxsAndUserIdx(
            List<Long> conversationIdxs,
            Long userId) {
        return jpaRepository.findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
    }

    @Override
    public List<ConversationParticipant> findParticipantsByConversationIdxsAndStatus(
            List<Long> conversationIdxs,
            ParticipantStatus status) {
        return jpaRepository.findParticipantsByConversationIdxsAndStatus(conversationIdxs, status);
    }

    @Override
    public void deleteAllInBatch(List<ConversationParticipant> participants) {
        jpaRepository.deleteAllInBatch(participants);
    }
}

