package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.RelatedType;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // 사용자별 활성 채팅방 조회 (탈퇴한 사용자 제외)
    @Query("SELECT DISTINCT c FROM Conversation c " +
           "INNER JOIN c.participants p " +
           "INNER JOIN p.user u " +
           "WHERE p.user.idx = :userId " +
           "  AND p.status = 'ACTIVE' " +
           "  AND c.status = :status " +
           "  AND c.isDeleted = false " +
           "  AND u.isDeleted = false " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC")
    List<Conversation> findActiveConversationsByUser(
        @Param("userId") Long userId,
        @Param("status") ConversationStatus status);

    // 채팅방 타입별 조회
    List<Conversation> findByConversationTypeAndStatusAndIsDeletedFalse(
        ConversationType conversationType,
        ConversationStatus status);

    // 관련 엔티티로 조회
    Optional<Conversation> findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(
        RelatedType relatedType,
        Long relatedIdx);

    // 관련 엔티티로 여러 개 조회
    List<Conversation> findByRelatedTypeAndRelatedIdxInAndIsDeletedFalse(
        RelatedType relatedType,
        List<Long> relatedIdxs);

    // 두 사용자 간 1:1 채팅방 조회
    @Query("SELECT DISTINCT c FROM Conversation c " +
           "INNER JOIN c.participants p1 ON p1.user.idx = :user1Idx AND p1.status = 'ACTIVE' " +
           "INNER JOIN c.participants p2 ON p2.user.idx = :user2Idx AND p2.status = 'ACTIVE' " +
           "WHERE c.conversationType = 'DIRECT' " +
           "  AND c.status = 'ACTIVE' " +
           "  AND c.isDeleted = false")
    Optional<Conversation> findDirectConversationBetweenUsers(
        @Param("user1Idx") Long user1Idx,
        @Param("user2Idx") Long user2Idx);

    // 채팅방 참여자 수 조회
    @Query("SELECT c.idx, COUNT(p) FROM Conversation c " +
           "LEFT JOIN c.participants p " +
           "WHERE c.idx IN :conversationIdxs " +
           "  AND (p IS NULL OR p.status = 'ACTIVE') " +
           "GROUP BY c.idx")
    List<Object[]> countParticipantsByConversationIdxs(@Param("conversationIdxs") List<Long> conversationIdxs);
}

