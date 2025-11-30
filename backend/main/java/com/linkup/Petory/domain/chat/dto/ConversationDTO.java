package com.linkup.Petory.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

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
}

