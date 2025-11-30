package com.linkup.Petory.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.file.dto.FileDTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
    private Long idx;
    private Long conversationIdx;
    private Long senderIdx;
    private String senderUsername;
    private String senderProfileImageUrl;
    private String messageType;  // TEXT, IMAGE, FILE, SYSTEM, NOTICE
    private String content;
    private Long replyToMessageIdx;
    private ChatMessageDTO replyToMessage;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 읽음 상태
    private Boolean isRead;
    private LocalDateTime readAt;
    private List<MessageReadStatusDTO> readStatuses;

    // 파일 첨부 (이미지, 파일)
    private List<FileDTO> attachments;

    // 탈퇴한 사용자 표시용
    private Boolean isDeletedSender;
}

