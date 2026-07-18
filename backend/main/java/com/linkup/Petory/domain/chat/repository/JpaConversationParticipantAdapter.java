package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;

import lombok.RequiredArgsConstructor;

/**
 * ConversationParticipantRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaConversationParticipantAdapter implements ConversationParticipantRepository {

    private final SpringDataJpaConversationParticipantRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public ConversationParticipant save(ConversationParticipant participant) {
        return jpaRepository.save(participant);
    }

    @SuppressWarnings("null")
    @Override
    public Optional<ConversationParticipant> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @SuppressWarnings("null")
    @Override
    public void delete(ConversationParticipant participant) {
        jpaRepository.delete(participant);
    }

    @SuppressWarnings("null")
    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<ConversationParticipant> findByConversationIdxAndStatus(
            Long conversationIdx,
            ParticipantStatus status) {
        return jpaRepository.findByConversationIdxAndStatus(conversationIdx, status);
    }

    @Override
    public Optional<ConversationParticipant> findByConversationIdxAndUserIdx(
            Long conversationIdx,
            Long userId) {
        return jpaRepository.findByConversationIdxAndUserIdx(conversationIdx, userId);
    }

    @Override
    public void incrementUnreadCount(Long conversationIdx, Long senderUserId) {
        jpaRepository.incrementUnreadCount(conversationIdx, senderUserId);
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

    @SuppressWarnings("null")
    @Override
    public void deleteAllInBatch(List<ConversationParticipant> participants) {
        jpaRepository.deleteAllInBatch(participants);
    }
}
