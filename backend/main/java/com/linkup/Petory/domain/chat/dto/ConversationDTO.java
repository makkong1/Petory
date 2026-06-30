package com.linkup.Petory.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/** 대화방 응답 DTO. 참여자 목록·읽지 않은 메시지 수·마지막 메시지 미리보기를 포함한다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDTO {
    private Long idx;
    private String conversationType;  // DIRECT, GROUP, CARE_REQUEST, etc.
    private String title;
    private String relatedType;
    private Long relatedIdx;
    private String status;  // ACTIVE, CLOSED, ARCHIVED
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 참여자 정보
    private List<ConversationParticipantDTO> participants;
    private Integer participantCount;

    // 현재 사용자의 읽지 않은 메시지 수
    private Integer unreadCount;

    // 마지막 메시지 정보
    private ChatMessageDTO lastMessage;

    /** 채팅방 참여자 중 제재 중인 사용자가 있을 때 true. 프론트엔드 안내 표시용. */
    @Builder.Default
    private boolean hasSanctionedParticipant = false;
}

