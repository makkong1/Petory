package com.linkup.Petory.domain.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * ChatMessage 도메인 Repository 인터페이스입니다.
 */
public interface ChatMessageRepository {

    // 기본 CRUD 메서드
    ChatMessage save(ChatMessage chatMessage);

    List<ChatMessage> saveAll(List<ChatMessage> chatMessages);

    Optional<ChatMessage> findById(Long id);

    void delete(ChatMessage chatMessage);

    void deleteById(Long id);

    /**
     * 채팅방별 메시지 조회 (페이징, 최신순)
     */
    Page<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(
        Long conversationIdx,
        Pageable pageable);

    /**
     * 채팅방별 메시지 조회 (커서 기반 페이징, 최신순)
     */
    List<ChatMessage> findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(
        Long conversationIdx,
        LocalDateTime beforeDate,
        Pageable pageable);

    /**
     * 채팅방별 메시지 조회 (특정 시점 이후, 페이징, 최신순) - 재참여 시 사용
     */
    Page<ChatMessage> findByConversationIdxAndCreatedAtAfterOrderByCreatedAtDesc(
        Long conversationIdx,
        LocalDateTime afterDate,
        Pageable pageable);

    /**
     * 채팅방별 메시지 조회 (전체, 최신순)
     */
    List<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(Long conversationIdx);

    /**
     * 채팅방의 가장 최신 메시지 조회
     */
    ChatMessage findTopByConversationIdxOrderByCreatedAtDesc(Long conversationIdx);

    /**
     * 사용자별 메시지 조회
     */
    List<ChatMessage> findBySenderAndIsDeletedFalseOrderByCreatedAtDesc(Users sender);

    /**
     * 메시지 타입별 조회
     */
    List<ChatMessage> findByConversationAndMessageTypeAndIsDeletedFalse(
        Conversation conversation,
        MessageType messageType);

    /**
     * 채팅방별 읽지 않은 메시지 수 조회
     */
    Long countUnreadMessages(Long conversationIdx, Long userId);

    /**
     * 여러 채팅방의 최신 메시지 조회 (배치) - Sender 포함
     */
    List<ChatMessage> findLatestMessagesByConversationIdxs(List<Long> conversationIdxs);

    /**
     * 메시지 검색 (Full-Text Search)
     */
    List<ChatMessage> searchMessagesByKeyword(
        Long conversationIdx,
        String keyword);
}

