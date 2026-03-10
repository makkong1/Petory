package com.linkup.Petory.domain.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @RepositoryMethod("채팅 메시지: 채팅방별 페이징 조회")
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

    @RepositoryMethod("채팅 메시지: 채팅방별 커서 페이징 조회")
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

    @RepositoryMethod("채팅 메시지: 채팅방별 시점 이후 페이징 (재참여)")
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

    @RepositoryMethod("채팅 메시지: 채팅방별 전체 조회")
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "LEFT JOIN FETCH m.replyToMessage " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(@Param("conversationIdx") Long conversationIdx);

    @RepositoryMethod("채팅 메시지: 채팅방별 최신 메시지 조회")
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender s " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.isDeleted = false " +
           "  AND s.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    ChatMessage findTopByConversationIdxOrderByCreatedAtDesc(@Param("conversationIdx") Long conversationIdx);

    @RepositoryMethod("채팅 메시지: 발신자별 조회")
    List<ChatMessage> findBySenderAndIsDeletedFalseOrderByCreatedAtDesc(Users sender);

    @RepositoryMethod("채팅 메시지: 채팅방+타입별 조회")
    List<ChatMessage> findByConversationAndMessageTypeAndIsDeletedFalse(
        Conversation conversation,
        MessageType messageType);

    @RepositoryMethod("채팅 메시지: 채팅방별 읽지 않은 메시지 수")
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.sender.idx != :userId " +
           "  AND m.isDeleted = false")
    Long countUnreadMessages(@Param("conversationIdx") Long conversationIdx, @Param("userId") Long userId);

    @RepositoryMethod("채팅 메시지: 채팅방별 최신 메시지 배치 조회")
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

    @RepositoryMethod("채팅 메시지: 키워드 검색")
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

