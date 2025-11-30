package com.linkup.Petory.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.MessageReadStatus;
import com.linkup.Petory.domain.user.entity.Users;

@Repository
public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {

    // 메시지별 읽음 상태 조회
    List<MessageReadStatus> findByMessage(ChatMessage message);

    // 사용자별 읽음 상태 조회
    List<MessageReadStatus> findByUserOrderByReadAtDesc(Users user);

    // 특정 메시지를 특정 사용자가 읽었는지 확인
    boolean existsByMessageAndUser(ChatMessage message, Users user);

    // 채팅방의 특정 사용자가 읽지 않은 메시지 ID 목록
    @Query("SELECT m.idx FROM ChatMessage m " +
           "WHERE m.conversation.idx = :conversationIdx " +
           "  AND m.sender.idx != :userId " +
           "  AND m.isDeleted = false " +
           "  AND NOT EXISTS (" +
           "    SELECT r FROM MessageReadStatus r " +
           "    WHERE r.message.idx = m.idx AND r.user.idx = :userId" +
           "  )")
    List<Long> findUnreadMessageIdxs(
        @Param("conversationIdx") Long conversationIdx,
        @Param("userId") Long userId);

    // 여러 메시지에 대한 읽음 상태 조회 (배치)
    @Query("SELECT r FROM MessageReadStatus r " +
           "JOIN FETCH r.user u " +
           "WHERE r.message.idx IN :messageIdxs")
    List<MessageReadStatus> findByMessageIdxs(@Param("messageIdxs") List<Long> messageIdxs);
}

