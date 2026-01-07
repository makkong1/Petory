package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.RelatedType;

/**
 * Conversation 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaConversationAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface ConversationRepository {

    // 기본 CRUD 메서드
    Conversation save(Conversation conversation);

    Optional<Conversation> findById(Long id);

    void delete(Conversation conversation);

    void deleteById(Long id);

    /**
     * 사용자별 활성 채팅방 조회 (탈퇴한 사용자 제외)
     */
    List<Conversation> findActiveConversationsByUser(
            Long userId,
            ConversationStatus status);

    /**
     * 채팅방 타입별 조회
     */
    List<Conversation> findByConversationTypeAndStatusAndIsDeletedFalse(
            ConversationType conversationType,
            ConversationStatus status);

    /**
     * 관련 엔티티로 조회
     */
    Optional<Conversation> findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(
            RelatedType relatedType,
            Long relatedIdx);

    /**
     * 관련 엔티티로 여러 개 조회
     */
    List<Conversation> findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
            RelatedType relatedType,
            List<Long> relatedIdxs);

    /**
     * 두 사용자 간 1:1 채팅방 조회
     */
    Optional<Conversation> findDirectConversationBetweenUsers(
            Long user1Idx,
            Long user2Idx);

    /**
     * 채팅방 참여자 수 조회
     * 반환값: List<Object[]> [conversationIdx, count]
     */
    List<Object[]> countParticipantsByConversationIdxs(List<Long> conversationIdxs);

    /**
     * 비관적 락을 사용한 채팅방 조회 (동시성 제어용)
     */
    Optional<Conversation> findByIdWithLock(Long idx);
}
