package com.linkup.Petory.domain.chat.dto;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipantDTO {
    private Long idx;
    private Long conversationIdx;
    private Long userIdx;
    private String username;
    private String userProfileImageUrl;
    private String role; // MEMBER, ADMIN, MODERATOR
    private Integer unreadCount;
    private Long lastReadMessageIdx;
    private LocalDateTime lastReadAt;
    private String status; // ACTIVE, LEFT, KICKED, MUTED
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private Boolean dealConfirmed;
    private LocalDateTime dealConfirmedAt;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 탈퇴한 사용자 표시용
    private Boolean isDeletedUser;
}
