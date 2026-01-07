package com.linkup.Petory.domain.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * ChatMessageRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaChatMessageAdapter implements ChatMessageRepository {

    private final SpringDataJpaChatMessageRepository jpaRepository;

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        return jpaRepository.save(chatMessage);
    }

    @Override
    public List<ChatMessage> saveAll(List<ChatMessage> chatMessages) {
        return jpaRepository.saveAll(chatMessages);
    }

    @Override
    public Optional<ChatMessage> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(ChatMessage chatMessage) {
        jpaRepository.delete(chatMessage);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Page<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(
            Long conversationIdx,
            Pageable pageable) {
        return jpaRepository.findByConversationIdxOrderByCreatedAtDesc(conversationIdx, pageable);
    }

    @Override
    public List<ChatMessage> findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(
            Long conversationIdx,
            LocalDateTime beforeDate,
            Pageable pageable) {
        return jpaRepository.findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(
                conversationIdx, beforeDate, pageable);
    }

    @Override
    public Page<ChatMessage> findByConversationIdxAndCreatedAtAfterOrderByCreatedAtDesc(
            Long conversationIdx,
            LocalDateTime afterDate,
            Pageable pageable) {
        return jpaRepository.findByConversationIdxAndCreatedAtAfterOrderByCreatedAtDesc(
                conversationIdx, afterDate, pageable);
    }

    @Override
    public List<ChatMessage> findByConversationIdxOrderByCreatedAtDesc(Long conversationIdx) {
        return jpaRepository.findByConversationIdxOrderByCreatedAtDesc(conversationIdx);
    }

    @Override
    public ChatMessage findTopByConversationIdxOrderByCreatedAtDesc(Long conversationIdx) {
        return jpaRepository.findTopByConversationIdxOrderByCreatedAtDesc(conversationIdx);
    }

    @Override
    public List<ChatMessage> findBySenderAndIsDeletedFalseOrderByCreatedAtDesc(Users sender) {
        return jpaRepository.findBySenderAndIsDeletedFalseOrderByCreatedAtDesc(sender);
    }

    @Override
    public List<ChatMessage> findByConversationAndMessageTypeAndIsDeletedFalse(
            Conversation conversation,
            MessageType messageType) {
        return jpaRepository.findByConversationAndMessageTypeAndIsDeletedFalse(conversation, messageType);
    }

    @Override
    public Long countUnreadMessages(Long conversationIdx, Long userId) {
        return jpaRepository.countUnreadMessages(conversationIdx, userId);
    }

    @Override
    public List<ChatMessage> findLatestMessagesByConversationIdxs(List<Long> conversationIdxs) {
        return jpaRepository.findLatestMessagesByConversationIdxs(conversationIdxs);
    }

    @Override
    public List<ChatMessage> searchMessagesByKeyword(
            Long conversationIdx,
            String keyword) {
        return jpaRepository.searchMessagesByKeyword(conversationIdx, keyword);
    }
}

