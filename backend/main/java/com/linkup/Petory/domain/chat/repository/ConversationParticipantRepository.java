package com.linkup.Petory.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;

/**
 * ConversationParticipant 도메인 Repository 인터페이스입니다.
 */
public interface ConversationParticipantRepository {

    // 기본 CRUD 메서드
    ConversationParticipant save(ConversationParticipant participant);

    Optional<ConversationParticipant> findById(Long id);

    void delete(ConversationParticipant participant);

    void deleteById(Long id);

    /**
     * 채팅방 ID로 활성 참여자 조회
     */
    List<ConversationParticipant> findByConversationIdxAndStatus(
            Long conversationIdx,
            ParticipantStatus status);

    /**
     * 채팅방 ID와 사용자 ID로 조회
     */
    Optional<ConversationParticipant> findByConversationIdxAndUserIdx(
            Long conversationIdx,
            Long userId);

    /**
     * 읽지 않은 메시지 수 증가
     */
    void incrementUnreadCount(Long conversationIdx, Long senderUserId);

    /**
     * 특정 채팅방의 특정 상태 참여자 수 조회
     */
    Integer countByConversationIdxAndStatus(
            Long conversationIdx,
            ParticipantStatus status);

    /**
     * 여러 채팅방의 특정 사용자 참여자 정보 배치 조회
     */
    List<ConversationParticipant> findParticipantsByConversationIdxsAndUserIdx(
            List<Long> conversationIdxs,
            Long userId);

    /**
     * 여러 채팅방의 활성 참여자 정보 배치 조회
     */
    List<ConversationParticipant> findParticipantsByConversationIdxsAndStatus(
            List<Long> conversationIdxs,
            ParticipantStatus status);

    /**
     * 배치 삭제 (테스트용)
     */
    void deleteAllInBatch(List<ConversationParticipant> participants);
}
