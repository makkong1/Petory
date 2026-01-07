package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.RelatedType;

import lombok.RequiredArgsConstructor;

/**
 * ConversationRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 ConversationRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisConversationAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoConversationAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaConversationAdapter implements ConversationRepository {

    private final SpringDataJpaConversationRepository jpaRepository;

    @Override
    public Conversation save(Conversation conversation) {
        return jpaRepository.save(conversation);
    }

    @Override
    public Optional<Conversation> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(Conversation conversation) {
        jpaRepository.delete(conversation);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Conversation> findActiveConversationsByUser(
            Long userId,
            ConversationStatus status) {
        return jpaRepository.findActiveConversationsByUser(userId, status);
    }

    @Override
    public List<Conversation> findByConversationTypeAndStatusAndIsDeletedFalse(
            ConversationType conversationType,
            ConversationStatus status) {
        return jpaRepository.findByConversationTypeAndStatusAndIsDeletedFalse(conversationType, status);
    }

    @Override
    public Optional<Conversation> findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(
            RelatedType relatedType,
            Long relatedIdx) {
        return jpaRepository.findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(relatedType, relatedIdx);
    }

    @Override
    public List<Conversation> findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
            RelatedType relatedType,
            List<Long> relatedIdxs) {
        return jpaRepository.findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(relatedType, relatedIdxs);
    }

    @Override
    public Optional<Conversation> findDirectConversationBetweenUsers(
            Long user1Idx,
            Long user2Idx) {
        return jpaRepository.findDirectConversationBetweenUsers(user1Idx, user2Idx);
    }

    @Override
    public List<Object[]> countParticipantsByConversationIdxs(List<Long> conversationIdxs) {
        return jpaRepository.countParticipantsByConversationIdxs(conversationIdxs);
    }

    @Override
    public Optional<Conversation> findByIdWithLock(Long idx) {
        return jpaRepository.findByIdWithLock(idx);
    }
}

