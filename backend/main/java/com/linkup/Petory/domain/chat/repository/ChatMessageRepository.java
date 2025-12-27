package com.linkup.Petory.domain.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.user.entity.Users;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 채팅방별 메시지 조회 (페이징, 최신순)
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "LEFT JOIN FETCH m.replyToMessage " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(
        @Param("conversationIdx") Long conversationIdx,
        Pageable pageable);

    // 채팅방별 메시지 조회 (커서 기반 페이징, 최신순)
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "LEFT JOIN FETCH m.replyToMessage " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.createdAt < :beforeDate " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(
        @Param("conversationIdx") Long conversationIdx,
        @Param("beforeDate") LocalDateTime beforeDate,
        Pageable pageable);

    // 채팅방별 메시지 조회 (특정 시점 이후, 페이징, 최신순) - 재참여 시 사용
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "LEFT JOIN FETCH m.replyToMessage " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.createdAt >= :afterDate " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByConversationIdxAndCreatedAtAfterOrderByCreatedAtDesc(
        @Param("conversationIdx") Long conversationIdx,
        @Param("afterDate") LocalDateTime afterDate,
        Pageable pageable);

    // 채팅방별 메시지 조회 (전체, 최신순)
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "LEFT JOIN FETCH m.replyToMessage " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(@Param("conversationIdx") Long conversationIdx);

    // 채팅방의 가장 최신 메시지 조회
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    ChatMessage findTopByConversationIdxOrderByCreatedAtDesc(@Param("conversationIdx") Long conversationIdx);

    // 사용자별 메시지 조회
    List<ChatMessage> findBySenderAndIsDeletedFalseOrderByCreatedAtDesc(Users sender);

    // 메시지 타입별 조회
    List<ChatMessage> findByConversationAndMessageTypeAndIsDeletedFalse(
        Conversation conversation,
        MessageType messageType);

    // 채팅방별 읽지 않은 메시지 수 조회
    // 참고: 실제로는 ConversationParticipant의 unreadCount를 사용하므로 이 메서드는 사용되지 않음
    // MessageReadStatus가 제거되어 쿼리도 단순화됨
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.sender.idx != :userId " +
           "  AND m.isDeleted = false")
    Long countUnreadMessages(@Param("conversationIdx") Long conversationIdx, @Param("userId") Long userId);

    // 여러 채팅방의 최신 메시지 조회 (배치) - Sender 포함
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "WHERE m.conversation.idx IN :conversationIdxs " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "  AND m.idx IN (" +
           "    SELECT MAX(m2.idx) FROM ChatMessage m2 " +
           "    WHERE m2.conversation.idx = m.conversation.idx " +
           "      AND m2.isDeleted = false" +
           "  )")
    List<ChatMessage> findLatestMessagesByConversationIdxs(@Param("conversationIdxs") List<Long> conversationIdxs);

    // 메시지 검색 (Full-Text Search)
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "JOIN FETCH m.conversation c " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "  AND (m.content LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> searchMessagesByKeyword(
        @Param("conversationIdx") Long conversationIdx,
        @Param("keyword") String keyword);
}

