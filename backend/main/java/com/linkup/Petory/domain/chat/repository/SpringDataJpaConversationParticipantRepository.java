package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    @RepositoryMethod("채팅 참여자: 채팅방별 활성 참여자 조회")
    List<ConversationParticipant> findByConversationAndStatus(
        Conversation conversation,
        ParticipantStatus status);

    @RepositoryMethod("채팅 참여자: 채팅방 ID로 활성 참여자 조회")
    @Query("SELECT p FROM ConversationParticipant p " +
           "JOIN FETCH p.user u " +
           "WHERE p.conversation.idx = :conversationIdx " +
           "  AND p.status = :status " +
           "  AND p.isDeleted = false " +
           "  AND u.isDeleted = false")
    List<ConversationParticipant> findByConversationIdxAndStatus(
        @Param("conversationIdx") Long conversationIdx,
        @Param("status") ParticipantStatus status);

    @RepositoryMethod("채팅 참여자: 사용자별 활성 참여 채팅방 조회")
    @Query("SELECT p FROM ConversationParticipant p " +
           "JOIN FETCH p.conversation c " +
           "WHERE p.user.idx = :userId " +
           "  AND p.status = 'ACTIVE' " +
           "  AND p.isDeleted = false " +
           "  AND c.isDeleted = false " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC")
    List<ConversationParticipant> findActiveParticipationsByUser(@Param("userId") Long userId);

    @RepositoryMethod("채팅 참여자: 채팅방+사용자로 조회")
    Optional<ConversationParticipant> findByConversationAndUser(
        Conversation conversation,
        Users user);

    @RepositoryMethod("채팅 참여자: 채팅방 ID+사용자 ID로 조회")
    @Query("SELECT p FROM ConversationParticipant p " +
           "JOIN FETCH p.conversation c " +
           "JOIN FETCH p.user u " +
           "WHERE p.conversation.idx = :conversationIdx " +
           "  AND p.user.idx = :userId")
    Optional<ConversationParticipant> findByConversationIdxAndUserIdx(
        @Param("conversationIdx") Long conversationIdx,
        @Param("userId") Long userId);

    @RepositoryMethod("채팅 참여자: 읽지 않은 메시지 채팅방 조회")
    @Query("SELECT p FROM ConversationParticipant p " +
           "JOIN FETCH p.conversation c " +
           "WHERE p.user.idx = :userId " +
           "  AND p.status = 'ACTIVE' " +
           "  AND p.unreadCount > 0 " +
           "  AND p.isDeleted = false " +
           "  AND c.isDeleted = false " +
           "ORDER BY c.lastMessageAt DESC")
    List<ConversationParticipant> findUnreadConversationsByUser(@Param("userId") Long userId);

    @RepositoryMethod("채팅 참여자: 읽음 처리")
    @Modifying
    @Query("UPDATE ConversationParticipant p SET p.unreadCount = 0, p.lastReadAt = CURRENT_TIMESTAMP " +
           "WHERE p.conversation.idx = :conversationIdx AND p.user.idx = :userId")
    void markAsRead(@Param("conversationIdx") Long conversationIdx, @Param("userId") Long userId);

    @RepositoryMethod("채팅 참여자: 읽지 않은 메시지 수 증가")
    @Modifying
    @Query("UPDATE ConversationParticipant p SET p.unreadCount = p.unreadCount + 1 " +
           "WHERE p.conversation.idx = :conversationIdx AND p.user.idx != :senderUserId AND p.status = 'ACTIVE'")
    void incrementUnreadCount(@Param("conversationIdx") Long conversationIdx, @Param("senderUserId") Long senderUserId);

    @RepositoryMethod("채팅 참여자: 채팅방별 활성 참여자 수 배치 조회")
    @Query("SELECT p.conversation.idx, COUNT(p) FROM ConversationParticipant p " +
           "WHERE p.conversation.idx IN :conversationIdxs " +
           "  AND p.status = 'ACTIVE' " +
           "  AND p.isDeleted = false " +
           "GROUP BY p.conversation.idx")
    List<Object[]> countActiveParticipantsByConversationIdxs(@Param("conversationIdxs") List<Long> conversationIdxs);

    @RepositoryMethod("채팅 참여자: 채팅방별 상태별 참여자 수 조회")
    @Query("SELECT COUNT(p) FROM ConversationParticipant p " +
           "WHERE p.conversation.idx = :conversationIdx " +
           "  AND p.status = :status " +
           "  AND p.isDeleted = false")
    Integer countByConversationIdxAndStatus(
        @Param("conversationIdx") Long conversationIdx,
        @Param("status") ParticipantStatus status);

    @RepositoryMethod("채팅 참여자: 채팅방 목록+사용자 조회 (배치)")
    @Query("SELECT p FROM ConversationParticipant p " +
           "JOIN FETCH p.conversation c " +
           "JOIN FETCH p.user u " +
           "WHERE p.conversation.idx IN :conversationIdxs " +
           "  AND p.user.idx = :userId " +
           "  AND p.isDeleted = false " +
           "  AND u.isDeleted = false")
    List<ConversationParticipant> findParticipantsByConversationIdxsAndUserIdx(
        @Param("conversationIdxs") List<Long> conversationIdxs,
        @Param("userId") Long userId);

    @RepositoryMethod("채팅 참여자: 채팅방 목록+상태 조회 (배치)")
    @Query("SELECT p FROM ConversationParticipant p " +
           "JOIN FETCH p.user u " +
           "WHERE p.conversation.idx IN :conversationIdxs " +
           "  AND p.status = :status " +
           "  AND p.isDeleted = false " +
           "  AND u.isDeleted = false")
    List<ConversationParticipant> findParticipantsByConversationIdxsAndStatus(
        @Param("conversationIdxs") List<Long> conversationIdxs,
        @Param("status") ParticipantStatus status);
}

